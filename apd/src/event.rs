use crate::sepolicy::{apply_folkpatch_extra_rules, get_policy_main};
use anyhow::{Context, Result};
use libc::SIGPWR;
use log::{info, warn};
use notify::{
    Config, Event, EventKind, INotifyWatcher, RecursiveMode, Watcher,
    event::{ModifyKind, RenameMode},
};
use signal_hook::{consts::signal::*, iterator::Signals};
use std::process::Stdio;
use std::{
    env, fs,
    os::unix::{fs::PermissionsExt, process::CommandExt},
    path::{Path, PathBuf},
    process::Command,
    sync::{Arc, Mutex},
    thread,
    time::Duration,
};

use crate::{
    assets, defs, lua, metamodule, module, restorecon, supercall,
    supercall::{init_load_su_path, refresh_ap_package_list},
    utils::{self, switch_cgroups},
};

fn migrate_file(old: &str, new: &str) -> Result<()> {
    let old_path = Path::new(old);
    if !old_path.exists() {
        return Ok(());
    }
    if let Some(parent) = Path::new(new).parent() {
        fs::create_dir_all(parent)?;
    }
    if !Path::new(new).exists() && fs::rename(old_path, new).is_err() {
        fs::copy(old_path, new)?;
    }
    if Path::new(new).exists() {
        let _ = fs::remove_file(old_path);
    }
    Ok(())
}

fn migrate_legacy_feature_paths() {
    let files = [
        (defs::LEGACY_HIDE_SERVICE_FILE, defs::HIDE_SERVICE_FILE),
        (defs::LEGACY_UMOUNT_SERVICE_FILE, defs::UMOUNT_SERVICE_FILE),
        (defs::LEGACY_UMOUNT_PATH_FILE, defs::UMOUNT_PATH_FILE),
        (defs::LEGACY_FPD_PATH, defs::HIDE_BINARY_PATH),
        (
            defs::LEGACY_UTS_SPOOF_ENABLE_FILE,
            defs::UTS_SPOOF_ENABLE_FILE,
        ),
        (
            defs::LEGACY_UTS_SPOOF_CONFIG_FILE,
            defs::UTS_SPOOF_CONFIG_FILE,
        ),
        (
            defs::LEGACY_UTS_SPOOF_BOOT_PENDING,
            defs::UTS_SPOOF_BOOT_PENDING,
        ),
        (
            defs::LEGACY_UTS_SPOOF_RETRY_FILE,
            defs::UTS_SPOOF_RETRY_FILE,
        ),
        (
            defs::LEGACY_PATHHIDE_ENABLE_FILE,
            defs::PATHHIDE_ENABLE_FILE,
        ),
        (defs::LEGACY_PATHHIDE_PATHS_FILE, defs::PATHHIDE_PATHS_FILE),
        (defs::LEGACY_PATHHIDE_UIDS_FILE, defs::PATHHIDE_UIDS_FILE),
        (
            defs::LEGACY_PATHHIDE_UID_MODE_FILE,
            defs::PATHHIDE_UID_MODE_FILE,
        ),
        (
            defs::LEGACY_PATHHIDE_FILTER_SYSTEM_FILE,
            defs::PATHHIDE_FILTER_SYSTEM_FILE,
        ),
        (defs::LEGACY_PATHHIDE_RETRY_FILE, defs::PATHHIDE_RETRY_FILE),
        (
            defs::LEGACY_NETISOLATE_ENABLE_FILE,
            defs::NETISOLATE_ENABLE_FILE,
        ),
        (
            defs::LEGACY_NETISOLATE_UIDS_FILE,
            defs::NETISOLATE_UIDS_FILE,
        ),
    ];

    for (old, new) in files {
        if let Err(error) = migrate_file(old, new) {
            warn!("runtime configuration migration failed: {error}");
        }
    }
    let _ = fs::remove_dir(defs::LEGACY_PATHHIDE_DIR);
    let _ = fs::remove_dir(defs::LEGACY_NETISOLATE_DIR);
    let _ = fs::remove_dir("/data/adb/fp/bin");
    let _ = fs::remove_dir("/data/adb/fp");
}

fn fpd_path() -> &'static str {
    if Path::new(defs::HIDE_BINARY_PATH).exists() {
        defs::HIDE_BINARY_PATH
    } else if Path::new(defs::LEGACY_FPD_PATH).exists() {
        defs::LEGACY_FPD_PATH
    } else {
        defs::HIDE_BINARY_PATH
    }
}

fn uts_is_active() -> bool {
    let Ok(raw) = fs::read_to_string(defs::UTS_SPOOF_CONFIG_FILE) else {
        return false;
    };
    let Ok(config) = serde_json::from_str::<serde_json::Value>(&raw) else {
        return false;
    };
    let release = config.get("release").and_then(|v| v.as_str()).unwrap_or("");
    let version = config.get("version").and_then(|v| v.as_str()).unwrap_or("");
    if release.is_empty() && version.is_empty() {
        return false;
    }
    let mut uts: libc::utsname = unsafe { std::mem::zeroed() };
    if unsafe { libc::uname(&mut uts) } != 0 {
        return false;
    }
    let current_release =
        unsafe { std::ffi::CStr::from_ptr(uts.release.as_ptr()) }.to_string_lossy();
    let current_version =
        unsafe { std::ffi::CStr::from_ptr(uts.version.as_ptr()) }.to_string_lossy();
    (release.is_empty() || current_release == release)
        && (version.is_empty() || current_version == version)
}

fn property_policy_unresolved(
    current: impl IntoIterator<Item = Option<String>>,
    expected: impl IntoIterator<Item = &'static str>,
) -> bool {
    let mut supported = 0usize;
    let mut mismatched = 0usize;
    for (current, expected) in current.into_iter().zip(expected) {
        if let Some(current) = current {
            supported += 1;
            if current != expected {
                mismatched += 1;
            }
        }
    }
    supported > 0 && mismatched > 0
}

fn runtime_policy_unresolved(superkey: &Option<String>) -> usize {
    let mut unresolved = 0;

    if Path::new(defs::PATHHIDE_ENABLE_FILE).exists()
        && !supercall::pathhide_status(superkey).is_some_and(|(active, _)| active)
    {
        unresolved += 1;
    }
    if Path::new(defs::NETISOLATE_ENABLE_FILE).exists()
        && !supercall::netisolate_status(superkey).is_some_and(|(active, _)| active)
    {
        unresolved += 1;
    }
    if Path::new(defs::UTS_SPOOF_ENABLE_FILE).exists() && !uts_is_active() {
        unresolved += 1;
    }
    if Path::new(defs::HIDE_SERVICE_FILE).exists() {
        let expected = [
            ("ro.boot.vbmeta.device_state", "locked"),
            ("ro.boot.verifiedbootstate", "green"),
            ("ro.boot.flash.locked", "1"),
            ("ro.boot.veritymode", "enforcing"),
        ];
        let current = expected.iter().map(|(key, _)| crate::utils::getprop(key));
        let expected_values = expected.iter().map(|(_, value)| *value);
        if !Path::new(fpd_path()).exists() || property_policy_unresolved(current, expected_values) {
            unresolved += 1;
        }
    }
    if Path::new(defs::UMOUNT_SERVICE_FILE).exists() && !Path::new(fpd_path()).exists() {
        unresolved += 1;
    }
    unresolved
}

pub fn runtime_policy_status(superkey: &Option<String>) -> serde_json::Value {
    let pathhide = supercall::pathhide_status(superkey);
    let netisolate = supercall::netisolate_status(superkey);
    let audit_count = supercall::su_audit_count(superkey);
    serde_json::json!({
        "superuser_stats": {
            "ready": audit_count.is_some(),
            "entries": audit_count,
        },
        "pathhide": {
            "configured": Path::new(defs::PATHHIDE_ENABLE_FILE).exists(),
            "active": pathhide.map(|v| v.0),
            "entries": pathhide.map(|v| v.1),
        },
        "netisolate": {
            "configured": Path::new(defs::NETISOLATE_ENABLE_FILE).exists(),
            "active": netisolate.map(|v| v.0),
            "entries": netisolate.map(|v| v.1),
        },
        "kernel_spoof": {
            "configured": Path::new(defs::UTS_SPOOF_ENABLE_FILE).exists(),
            "active": uts_is_active(),
        },
        "hide": {
            "configured": Path::new(defs::HIDE_SERVICE_FILE).exists(),
            "binary": Path::new(fpd_path()).exists(),
        },
        "umount": {
            "configured": Path::new(defs::UMOUNT_SERVICE_FILE).exists(),
            "binary": Path::new(fpd_path()).exists(),
            "paths": Path::new(defs::UMOUNT_PATH_FILE).exists(),
        },
        "unresolved": runtime_policy_unresolved(superkey),
    })
}

pub fn report_kernel(superkey: Option<String>, event: &str, state: &str) {
    let args = [
        superkey.unwrap_or("su".to_string()),
        "event".to_string(),
        event.to_string(),
        state.to_string(),
    ];
    let args_ref: Vec<&str> = args.iter().map(|s| s.as_str()).collect();
    // Best-effort notification to the kernel; a failed report must not abort
    // boot stages such as post-fs-data.
    if let Err(e) = utils::run_command("truncate", &args_ref, None)
        .and_then(|mut child| child.wait().map_err(anyhow::Error::from))
    {
        warn!("report kernel event {event}/{state} failed: {e}");
    }
}

fn setup_fp_directories() -> Result<()> {
    utils::ensure_dir_with_perms(
        Path::new(defs::BINARY_DIR),
        Path::new(defs::WORKING_DIR),
        0o755,
    )?;
    Ok(())
}

fn setup_logging() -> Result<()> {
    let log_dir = Path::new(defs::APATCH_LOG_FOLDER);
    if !log_dir.exists() {
        fs::create_dir(log_dir).expect("Failed to create log folder");
        let permissions = fs::Permissions::from_mode(0o700);
        fs::set_permissions(log_dir, permissions).expect("Failed to set permissions");
    }

    let command_string = format!(
        "cd {}; rm -f *.last; [ -f dmesg.log ] && mv dmesg.log dmesg.last; [ -f logcat.log ] && mv logcat.log logcat.last; [ -f locat.log ] && mv locat.log logcat.last; rm -f *.log *.old.log",
        defs::APATCH_LOG_FOLDER
    );
    let result = utils::run_command("sh", &["-c", &command_string], None)?.wait()?;
    if result.success() {
        info!("Successfully rotated logs.");
    } else {
        info!("Failed to rotate logs.");
    }

    let logcat_path = format!("{}logcat.log", defs::APATCH_LOG_FOLDER);
    let dmesg_path = format!("{}dmesg.log", defs::APATCH_LOG_FOLDER);
    let bootlog = fs::File::create(&dmesg_path)?;

    let _ = unsafe {
        Command::new("timeout")
            .process_group(0)
            .pre_exec(|| {
                switch_cgroups();
                Ok(())
            })
            .args(vec![
                "-s",
                "9",
                "45s",
                "logcat",
                "-b",
                "main,system,crash",
                "DrmLibFs:S",
                "-f",
                &logcat_path,
                "logcatcher-bootlog:S",
            ])
            .spawn()
    };
    let _ = unsafe {
        Command::new("timeout")
            .process_group(0)
            .pre_exec(|| {
                switch_cgroups();
                Ok(())
            })
            .args(["-s", "9", "120s", "dmesg", "-w"])
            .stdout(Stdio::from(bootlog))
            .spawn()
    };

    Ok(())
}

fn disable_all_modules_safe() {
    if let Err(e) = module::disable_all_modules() {
        warn!("disable all modules failed: {e}");
    }
}

fn exec_fpd_hide() {
    let fpd = fpd_path();
    if !Path::new(defs::HIDE_SERVICE_FILE).exists() {
        return;
    }
    info!("runtime property policy applying");
    if !Path::new(fpd).exists() {
        warn!("fpd binary not found, please install it manually");
        return;
    }
    let result = Command::new(fpd).arg("-hide").status();
    match result {
        Ok(status) => {
            if status.success() {
                info!("runtime property policy applied");
            } else {
                warn!(
                    "runtime property policy exited with status: {:?}",
                    status.code()
                );
            }
        }
        Err(e) => {
            warn!("runtime property policy failed: {}", e);
        }
    }
}

fn exec_fpd_umount() {
    let fpd = fpd_path();
    if !Path::new(defs::UMOUNT_SERVICE_FILE).exists() {
        return;
    }
    info!("runtime mount policy applying");
    if !Path::new(fpd).exists() {
        warn!("fpd binary not found, please install it manually");
        return;
    }
    let mut command = Command::new(fpd);
    command
        .arg("-umount")
        .stdout(Stdio::piped())
        .stderr(Stdio::piped());
    if let Err(error) = utils::command_in_mnt_ns(&mut command, 1) {
        warn!("runtime mount namespace unavailable: {error}");
        return;
    }
    let result = command.output();
    match result {
        Ok(output) => {
            let stdout = String::from_utf8_lossy(&output.stdout);
            let stderr = String::from_utf8_lossy(&output.stderr);
            if output.status.success() {
                info!("runtime mount policy applied");
            } else {
                warn!(
                    "runtime mount policy exited with status: {:?}",
                    output.status.code()
                );
            }
            if !stdout.trim().is_empty() {
                let _ = stdout;
            }
            if !stderr.trim().is_empty() {
                let _ = stderr;
            }
        }
        Err(e) => {
            warn!("runtime mount policy failed: {}", e);
        }
    }
}

pub fn on_post_data_fs(superkey: Option<String>) -> Result<()> {
    info!("post-fs-data");
    migrate_legacy_feature_paths();
    utils::umask(0);
    report_kernel(superkey.clone(), "post-fs-data", "before");

    if let Err(e) = setup_fp_directories() {
        warn!("setup_fp_directories failed: {e}");
    }

    init_load_su_path(&superkey);
    supercall::apply_sucompat(&superkey);

    let mut sepol = get_policy_main(&["magiskpolicy".to_string(), "--live".to_string()])?;
    sepol.magisk_rules();
    apply_folkpatch_extra_rules(&mut sepol);
    sepol
        .to_file("/sys/fs/selinux/load")
        .context("Cannot apply policy")?;

    info!("Re-privilege apd profile after injecting sepolicy");
    supercall::privilege_apd_profile(&superkey);

    // Apply UTS namespace spoofing if configured
    supercall::apply_uts_spoof(&superkey);

    // Apply netisolate config if enabled
    supercall::apply_netisolate(&superkey);

    // Clear all temporary module configs early
    if let Err(e) = crate::module_config::clear_all_temp_configs() {
        warn!("clear temp configs failed: {e}");
    }

    if utils::has_magisk() {
        warn!("Magisk detected, skip post-fs-data!");
        report_kernel(superkey.clone(), "post-fs-data", "after");
        return Ok(());
    }

    setup_logging()?;

    for key in ["KERNELPATCH_VERSION", "KERNEL_VERSION"] {
        match env::var(key) {
            Ok(value) => println!("{key}: {value}"),
            Err(_) => println!("{key} not found"),
        }
    }

    let safe_mode = utils::is_safe_mode(superkey.clone());

    if safe_mode {
        // we should still mount modules.img to `/data/adb/modules` in safe mode
        // becuase we may need to operate the module dir in safe mode
        warn!("safe mode, skip common post-fs-data.d scripts");
        // Not redundant with the disable below: ensure_binaries /
        // handle_updated_modules can still fail with `?` before reaching it,
        // and returning early with modules left enabled risks a bootloop.
        disable_all_modules_safe();
    } else {
        // Then exec common post-fs-data scripts
        if let Err(e) = module::exec_common_scripts("post-fs-data.d", true) {
            warn!("exec common post-fs-data scripts failed: {}", e);
        }
    }
    let module_update_dir = defs::MODULE_UPDATE_DIR; //save module place
    let module_dir = defs::MODULE_DIR; // run modules place
    let module_update_flag = Path::new(defs::WORKING_DIR).join(defs::UPDATE_FILE_NAME); // if update ,there will be renewed modules file
    assets::ensure_binaries().with_context(|| "binary missing")?;

    if Path::new(defs::MODULE_UPDATE_DIR).exists() {
        module::handle_updated_modules()?;
        fs::remove_dir_all(module_update_dir)?;
    }

    if safe_mode {
        warn!("safe mode, skip post-fs-data scripts and disable all modules!");
        disable_all_modules_safe();
        return Ok(());
    }

    if let Err(e) = module::prune_modules() {
        warn!("prune modules failed: {}", e);
    }

    if let Err(e) = restorecon::restorecon() {
        warn!("restorecon failed: {}", e);
    }

    // load sepolicy.rule
    if module::load_sepolicy_rule().is_err() {
        warn!("load sepolicy.rule failed");
    }
    let magic_mount_enabled = Path::new(defs::MAGIC_MOUNT_FILE).exists();
    if magic_mount_enabled {
        info!("Folk Mount API enabled; deferring mount to post-mount");
    } else {
        info!("Magic Mount disabled");
        if let Err(e) = metamodule::exec_mount_script(module_dir) {
            warn!("execute metamodule mount failed: {e}");
        }
    }

    exec_fpd_hide();

    // exec modules post-fs-data scripts
    // TODO: Add timeout
    if let Err(e) = module::exec_stage_script("post-fs-data", true) {
        warn!("exec post-fs-data scripts failed: {}", e);
    }
    if let Err(e) = lua::exec_stage_lua("post-fs-data", true) {
        warn!("Failed to exec post-fs-data lua: {}", e);
    }
    if let Err(e) = lua::exec_plugin_stage("post-fs-data") {
        warn!("Failed to exec plugin post-fs-data: {}", e);
    }
    // load system.prop
    if let Err(e) = module::load_system_prop() {
        warn!("load system.prop failed: {}", e);
    }

    info!("remove update flag");
    let _ = fs::remove_file(module_update_flag);

    run_stage("post-mount", superkey.clone(), true);

    if magic_mount_enabled && let Err(e) = crate::magic_mount::magic_mount(defs::AP_OVERLAY_SOURCE)
    {
        log::error!("Folk Mount failed: {e}");
    }

    report_kernel(superkey, "post-fs-data", "after");

    env::set_current_dir("/").with_context(|| "failed to chdir to /")?;

    Ok(())
}

fn run_stage(stage: &str, superkey: Option<String>, block: bool) {
    utils::umask(0);

    if utils::has_magisk() {
        warn!("Magisk detected, skip {stage}");
        return;
    }

    if utils::is_safe_mode(superkey.clone()) {
        warn!("safe mode, skip {stage} scripts");
        disable_all_modules_safe();
        return;
    }

    // execute metamodule stage script first (priority)
    if let Err(e) = metamodule::exec_stage_script(stage, block) {
        warn!("Failed to exec metamodule {stage} script: {e}");
    }

    if let Err(e) = module::exec_common_scripts(&format!("{stage}.d"), block) {
        warn!("Failed to exec common {stage} scripts: {e}");
    }
    if let Err(e) = module::exec_stage_script(stage, block) {
        warn!("Failed to exec {stage} scripts: {e}");
    }
    if let Err(e) = lua::exec_stage_lua(stage, block) {
        warn!("Failed to exec {stage} lua: {e}");
    }
    if let Err(e) = lua::exec_plugin_stage(stage) {
        warn!("Failed to exec plugin {stage}: {e}");
    }
}

pub fn on_services(superkey: Option<String>) -> Result<()> {
    info!("services");
    migrate_legacy_feature_paths();
    supercall::apply_sucompat(&superkey);

    if Path::new(defs::UTS_SPOOF_RETRY_FILE).exists() {
        info!("Retrying deferred UTS spoof apply from services stage");
        supercall::apply_uts_spoof(&superkey);
    }

    run_stage("service", superkey, false);

    Ok(())
}

fn run_uid_monitor() {
    info!("Trigger run_uid_monitor!");

    let mut command = Command::new("/data/adb/apd");
    utils::background_command(&mut command);
    match command.arg("uid-listener").spawn() {
        Ok(mut child) => {
            thread::spawn(move || {
                let _ = child.wait();
            });
        }
        Err(error) => warn!("Cannot start UID listener: {error}"),
    }
}

pub fn on_boot_completed(superkey: Option<String>) -> Result<()> {
    info!("boot-completed");
    migrate_legacy_feature_paths();
    supercall::apply_sucompat(&superkey);

    // Clear UTS spoof boot safety flag — boot completed successfully
    if Path::new(defs::UTS_SPOOF_BOOT_PENDING).exists() {
        let _ = std::fs::remove_file(defs::UTS_SPOOF_BOOT_PENDING);
        info!("UTS spoof boot safety flag cleared");
    }

    run_stage("boot-completed", superkey.clone(), false);

    if Path::new(defs::PATHHIDE_ENABLE_FILE).exists() {
        info!("Applying pathhide from boot-completed stage");
        supercall::apply_pathhide(&superkey);
    }

    if Path::new(defs::UTS_SPOOF_RETRY_FILE).exists() {
        info!("Retrying deferred UTS spoof apply from boot-completed stage");
        supercall::apply_uts_spoof(&superkey);
    }

    exec_fpd_umount();

    run_uid_monitor();
    let unresolved = runtime_policy_unresolved(&superkey);
    if unresolved == 0 {
        info!("runtime policy check complete");
    } else {
        warn!("runtime policy check incomplete: {unresolved}");
    }
    Ok(())
}

pub fn on_manager_boot_completed(superkey: Option<String>) -> Result<()> {
    info!("manager boot fallback");
    migrate_legacy_feature_paths();

    let superkey = superkey.or_else(|| {
        info!("Manager boot fallback invoked without explicit authentication key");
        Some("su".to_string())
    });

    supercall::apply_sucompat(&superkey);

    if Path::new(defs::UTS_SPOOF_BOOT_PENDING).exists() {
        let _ = std::fs::remove_file(defs::UTS_SPOOF_BOOT_PENDING);
        info!("UTS spoof boot safety flag cleared by manager boot fallback");
    }

    if Path::new(defs::PATHHIDE_ENABLE_FILE).exists() {
        info!("Manager boot fallback: applying pathhide");
        supercall::apply_pathhide(&superkey);
    }

    if Path::new(defs::UTS_SPOOF_ENABLE_FILE).exists()
        || Path::new(defs::UTS_SPOOF_RETRY_FILE).exists()
    {
        info!("Manager boot fallback: applying UTS spoof");
        supercall::apply_uts_spoof(&superkey);
        if Path::new(defs::UTS_SPOOF_BOOT_PENDING).exists() {
            let _ = std::fs::remove_file(defs::UTS_SPOOF_BOOT_PENDING);
            info!("UTS spoof boot safety flag cleared after manager boot fallback apply");
        }
    }

    if Path::new(defs::NETISOLATE_ENABLE_FILE).exists() {
        info!("Manager boot fallback: applying netisolate");
        supercall::apply_netisolate(&superkey);
    }

    if Path::new(defs::HIDE_SERVICE_FILE).exists() {
        exec_fpd_hide();
    }

    if Path::new(defs::UMOUNT_SERVICE_FILE).exists() {
        exec_fpd_umount();
    }

    let unresolved = runtime_policy_unresolved(&superkey);
    if unresolved > 0 {
        anyhow::bail!("runtime policy verification incomplete: {unresolved}");
    }
    info!("runtime policy check complete");
    Ok(())
}

pub fn start_uid_listener() -> Result<()> {
    use std::os::{fd::AsRawFd, unix::fs::OpenOptionsExt};
    let listener_lock = fs::OpenOptions::new()
        .write(true)
        .create(true)
        .truncate(false)
        .mode(0o600)
        .custom_flags(libc::O_NOFOLLOW | libc::O_CLOEXEC)
        .open("/data/adb/ap/uid-listener.lock")?;
    if unsafe { libc::flock(listener_lock.as_raw_fd(), libc::LOCK_EX | libc::LOCK_NB) } != 0 {
        let error = std::io::Error::last_os_error();
        if error.raw_os_error() == Some(libc::EWOULDBLOCK) {
            info!("UID listener already running; duplicate invocation ignored");
            return Ok(());
        }
        return Err(error.into());
    }

    info!("start_uid_listener triggered!");
    println!("[start_uid_listener] Registering...");

    // create inotify instance
    const SYS_PACKAGES_LIST_TMP: &str = "/data/system/packages.list.tmp";
    let sys_packages_list_tmp = PathBuf::from(&SYS_PACKAGES_LIST_TMP);
    let dir = PathBuf::from("/data/system");

    let (tx, rx) = std::sync::mpsc::channel();
    let tx_clone = tx.clone();
    let mutex = Arc::new(Mutex::new(()));

    {
        let mutex_clone = mutex.clone();
        thread::spawn(move || {
            let Ok(mut signals) = Signals::new([SIGTERM, SIGINT, SIGPWR]) else {
                warn!("Cannot register UID listener shutdown signals");
                return;
            };
            for sig in signals.forever() {
                log::warn!("[shutdown] Caught signal {sig}, refreshing package list...");
                let skey = c"su";
                refresh_ap_package_list(skey, &mutex_clone);
                if sig == SIGTERM || sig == SIGINT {
                    std::process::exit(0);
                }
            }
        });
    }

    let mut watcher = INotifyWatcher::new(
        move |ev: notify::Result<Event>| match ev {
            Ok(Event {
                kind: EventKind::Modify(ModifyKind::Name(RenameMode::Both)),
                paths,
                ..
            }) => {
                if paths.contains(&sys_packages_list_tmp) {
                    info!("[uid_monitor] System packages list changed, sending to tx...");
                    let _ = tx_clone.send(false);
                }
            }
            Err(err) => warn!("inotify error: {err}"),
            _ => (),
        },
        Config::default(),
    )?;

    watcher.watch(dir.as_ref(), RecursiveMode::NonRecursive)?;

    // Register first, so changes during the initial refresh remain queued.
    refresh_ap_package_list(c"su", &mutex);

    let mut debounce = false;
    while let Ok(delayed) = rx.recv() {
        if delayed {
            debounce = false;
            let skey = c"su";
            refresh_ap_package_list(skey, &mutex);
            report_kernel(None, "uid_listener", "package-list-updated");
        } else if !debounce {
            thread::sleep(Duration::from_secs(1));
            debounce = true;
            tx.send(true)?;
        }
    }

    Ok(())
}

/// Emulate a system reboot: restart the Android framework (`stop` / `start`)
/// and re-apply the service stage. Used by jailbreak mode so that a runtime-loaded
/// `kernelpatch.ko` stays active (a full reboot would drop it).
pub fn soft_reboot(superkey: Option<String>) -> Result<()> {
    use std::process::Command;

    // Detach from the caller (app root shell) first: `stop` tears down the
    // framework including the app/zygote tree this process was spawned from, so
    // without daemonizing the `start` below would never be reached.
    utils::daemonize()?;

    info!("emulating soft reboot!");
    utils::switch_mnt_ns(1)?;
    std::env::set_current_dir("/").with_context(|| "failed to chdir to /")?;

    if let Err(e) = crate::resetprop::set_prop("sys.boot_completed", "0") {
        warn!("reset boot completed failed: {e}");
    }

    info!("stop");
    let status = Command::new("stop").status().context("stop failed")?;
    if !status.success() {
        warn!("stop exited with status: {status}");
    }

    info!("post-fs-data");
    // Never abort the soft reboot here: the framework must always be restarted.
    // The daemonized stdin (dev null) keeps the supercall/truncate redirects from
    // blocking, so re-applying the boot stages is safe.
    if let Err(e) = on_post_data_fs(superkey.clone()) {
        warn!("post-fs-data failed during soft reboot: {e:#}");
    }

    info!("start");
    let status = Command::new("start").status().context("start failed")?;
    if !status.success() {
        warn!("start exited with status: {status}");
    }

    info!("services");
    on_services(superkey)?;

    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn unsupported_properties_do_not_mask_supported_mismatches() {
        let expected = ["locked", "green", "1", "enforcing"];

        assert!(!property_policy_unresolved(
            [None, None, None, None],
            expected
        ));
        assert!(!property_policy_unresolved(
            [
                Some("locked".into()),
                Some("green".into()),
                Some("1".into()),
                Some("enforcing".into()),
            ],
            expected
        ));
        assert!(property_policy_unresolved(
            [Some("unlocked".into()), None, None, None,],
            expected
        ));
        assert!(property_policy_unresolved(
            [Some("locked".into()), Some("orange".into()), None, None,],
            expected
        ));
    }
}

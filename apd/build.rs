use std::{env, fs::File, io::Write, path::Path, process::Command};

// app/src/main/cpp/version is the single source of the KernelPatch version;
// both this crate and the root build.gradle.kts derive their copy from it.
fn get_kp_version() -> (u32, u32, u32) {
    let header = std::fs::read_to_string("../app/src/main/cpp/version")
        .expect("Failed to read ../app/src/main/cpp/version");
    let parse = |name: &str| -> u32 {
        header
            .lines()
            .find_map(|line| {
                line.strip_prefix(format!("#define {name} ").as_str())
                    .and_then(|v| v.trim().parse().ok())
            })
            .unwrap_or_else(|| panic!("{name} not found in app/src/main/cpp/version"))
    };
    (parse("MAJOR"), parse("MINOR"), parse("PATCH"))
}

fn get_version_property(name: &str) -> Result<String, std::io::Error> {
    let content = std::fs::read_to_string("../version.properties")?;
    content
        .lines()
        .find_map(|line| {
            line.strip_prefix(&format!("{name}="))
                .map(str::trim)
                .filter(|value| !value.is_empty())
                .map(str::to_owned)
        })
        .ok_or_else(|| std::io::Error::other(format!("{name} not found in version.properties")))
}

fn get_git_version_name() -> Result<String, std::io::Error> {
    let output = Command::new("git")
        .args(["rev-parse", "--verify", "--short", "HEAD"])
        .output()?;
    let name = String::from_utf8(output.stdout)
        .map_err(|_| std::io::Error::other("Failed to read git version name"))?
        .trim()
        .to_owned();
    if name.is_empty() {
        return Err(std::io::Error::other(
            "Failed to determine git version name",
        ));
    }
    Ok(name)
}

fn get_git_version() -> Result<(u32, String), std::io::Error> {
    let floor: u32 = get_version_property("managerVersionFloor")?
        .parse()
        .map_err(|_| std::io::Error::other("Failed to parse managerVersionFloor"))?;
    let version_code = match env::var("APATCH_VERSION_CODE") {
        Ok(value) => value
            .parse()
            .map_err(|_| std::io::Error::other("Failed to parse APATCH_VERSION_CODE"))?,
        Err(_) => {
            let epoch: u32 = get_version_property("managerVersionEpoch")?
                .parse()
                .map_err(|_| std::io::Error::other("Failed to parse managerVersionEpoch"))?;
            let output = Command::new("git")
                .args(["rev-list", "--count", "HEAD"])
                .output()?;
            let commit_count: u32 = String::from_utf8(output.stdout)
                .map_err(|_| std::io::Error::other("Failed to read git count stdout"))?
                .trim()
                .parse()
                .map_err(|_| std::io::Error::other("Failed to parse git commit count"))?;
            epoch
                .checked_add(commit_count)
                .ok_or_else(|| std::io::Error::other("Version code overflow"))?
        }
    };
    if version_code <= floor {
        return Err(std::io::Error::other(format!(
            "Computed versionCode {version_code} is not greater than managerVersionFloor={floor}"
        )));
    }

    let version_name = match env::var("APATCH_VERSION_NAME") {
        Ok(value) => value,
        Err(_) => get_git_version_name()?,
    };
    Ok((version_code, version_name))
}

fn main() {
    // update VersionCode when git repository change
    println!("cargo:rerun-if-changed=../.git/HEAD");
    println!("cargo:rerun-if-changed=../.git/refs/");
    println!("cargo:rerun-if-changed=../app/src/main/cpp/version");
    println!("cargo:rerun-if-changed=../version.properties");

    let (code, name) = get_git_version().expect("Failed to determine FolkPatch version");
    let out_dir = env::var("OUT_DIR").expect("Failed to get $OUT_DIR");
    println!("out_dir: ${out_dir}");
    println!("code: ${code}");
    let out_dir = Path::new(&out_dir);
    File::create(Path::new(out_dir).join("VERSION_CODE"))
        .expect("Failed to create VERSION_CODE")
        .write_all(code.to_string().as_bytes())
        .expect("Failed to write VERSION_CODE");

    File::create(Path::new(out_dir).join("VERSION_NAME"))
        .expect("Failed to create VERSION_NAME")
        .write_all(name.trim().as_bytes())
        .expect("Failed to write VERSION_NAME");

    let (major, minor, patch) = get_kp_version();
    File::create(Path::new(out_dir).join("kp_version.rs"))
        .expect("Failed to create kp_version.rs")
        .write_all(
            format!(
                "pub const KP_MAJOR: i64 = {major};\n\
                 pub const KP_MINOR: i64 = {minor};\n\
                 pub const KP_PATCH: i64 = {patch};\n"
            )
            .as_bytes(),
        )
        .expect("Failed to write kp_version.rs");
}

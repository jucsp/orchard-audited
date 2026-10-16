#!/usr/bin/env bash
# SPDX-License-Identifier: AGPL-3.0-or-later
set -euo pipefail

# Packages the AppDir from package-linux-appimage.sh as an x86_64 rpm.
# Usage: package-linux-rpm.sh <AppDir> <output-dir> <version> [release]
appdir="$(realpath "${1:?pass the AppDir from package-linux-appimage.sh}")"
output_dir="$(realpath -m "${2:?pass an output directory}")"
version="${3:?pass the Orchard version, e.g. 1.0.0-canary.9}"
release="${4:-1}"
source_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
spec="$source_dir/packaging/rpm/orchard.spec"

[[ -x "$appdir/AppRun" && -x "$appdir/usr/bin/orchard" ]] || { echo "$appdir is not an Orchard AppDir" >&2; exit 1; }
[[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.]+)?$ ]] || { echo "Unsupported version: $version" >&2; exit 1; }
[[ "$release" =~ ^[0-9]+$ ]] || { echo "Release must be numeric: $release" >&2; exit 1; }
command -v rpmbuild >/dev/null || { echo "rpmbuild is required (apt-get install rpm)" >&2; exit 1; }
command -v readelf >/dev/null || { echo "readelf is required (binutils)" >&2; exit 1; }

# rpm forbids '-' in Version; '~' keeps 1.0.0~canary.9 sorting before 1.0.0.
rpm_version="${version/-/\~}"
mkdir -p "$output_dir"
work="$(mktemp -d "$output_dir/rpmbuild.XXXXXX")"
trap 'rm -rf -- "$work"' EXIT

cat > "$work/orchard.desktop" <<'EOF'
[Desktop Entry]
Type=Application
Name=Orchard
Exec=orchard %U
Icon=orchard
Categories=AudioVideo;Audio;Player;
EOF
cp "$source_dir/app/qml/assets/orchard-logo.png" "$work/orchard.png"

# AutoReqProv is off because the AppDir bundles its own libraries. Require only
# the sonames its ELF files need and do not carry (glibc, GL, X11, NSS, ...).
declare -A bundled=()
while IFS= read -r name; do bundled["$name"]=1; done < <(find "$appdir" \( -type f -o -type l \) -name '*.so*' -printf '%f\n')
mapfile -t needed < <(
  find "$appdir" -type f -print0 \
    | xargs -0 readelf -d 2>/dev/null \
    | sed -n 's/.*(NEEDED).*\[\(.*\)\]/\1/p' | sort -u
)
requires=()
for soname in "${needed[@]}"; do
  [[ -n "${bundled[$soname]:-}" ]] || requires+=("$soname()(64bit)")
done
requires_line="%{nil}"
[[ ${#requires[@]} -eq 0 ]] || requires_line="Requires: ${requires[*]}"

rpmbuild -bb "$spec" \
  --target x86_64 \
  --define "_topdir $work" \
  --define "appdir $appdir" \
  --define "orchard_version $rpm_version" \
  --define "orchard_release $release" \
  --define "orchard_requires $requires_line" \
  --define "desktop_file $work/orchard.desktop" \
  --define "icon_file $work/orchard.png"

mapfile -t rpms < <(find "$work/RPMS" -name '*.rpm' -print)
[[ ${#rpms[@]} -eq 1 ]] || { echo "Expected one rpm, found ${#rpms[@]}" >&2; exit 1; }
# Release asset names cannot keep '~', so the file name uses the upstream version.
target="$output_dir/orchard-$version-$release.x86_64.rpm"
mv -- "${rpms[0]}" "$target"
grep -qxF /usr/bin/orchard <(rpm -qpl "$target")
echo "$target"

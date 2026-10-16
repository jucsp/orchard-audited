# SPDX-License-Identifier: AGPL-3.0-or-later
# Built by scripts/package-linux-rpm.sh, which defines appdir, orchard_version,
# orchard_release, orchard_requires and the staged desktop and icon files.

# The AppDir ships prebuilt, already deployed binaries; rpm must not rewrite them.
%global debug_package %{nil}
%global __os_install_post %{nil}
%global _build_id_links none
%undefine __brp_mangle_shebangs

Name:           orchard
Version:        %{orchard_version}
Release:        %{orchard_release}
Summary:        Orchard music player (orchard-audited fork)
License:        AGPL-3.0-or-later
URL:            https://github.com/jucsp/orchard-audited
ExclusiveArch:  x86_64
AutoReqProv:    no
%{orchard_requires}

%description
Orchard desktop music player built from the orchard-audited fork. The
application tree is the same AppDir published as Orchard-Linux-x86_64.AppImage,
installed under /opt/orchard.

%install
mkdir -p %{buildroot}/opt/orchard %{buildroot}%{_bindir}
cp -a %{appdir}/. %{buildroot}/opt/orchard/
ln -s /opt/orchard/AppRun %{buildroot}%{_bindir}/orchard
install -Dm0644 %{desktop_file} %{buildroot}%{_datadir}/applications/orchard.desktop
install -Dm0644 %{icon_file} %{buildroot}%{_datadir}/icons/hicolor/512x512/apps/orchard.png

%files
/opt/orchard
%{_bindir}/orchard
%{_datadir}/applications/orchard.desktop
%{_datadir}/icons/hicolor/512x512/apps/orchard.png

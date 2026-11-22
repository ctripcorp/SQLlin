#!/usr/bin/env bash
#
# Builds the static SQLite library that SQLlin's native tests link on Linux and Windows, from the sources of a SQLite
# version that sqlite.org publishes. The libraries aren't in the repository: CI builds them with this script, see
# .github/actions/sqlite, and so does whoever runs the native tests on a Linux or Windows machine.
#
# Usage: scripts/build-sqlite.sh <version> <linux|windows> [<output>]
#
#   scripts/build-sqlite.sh 3.51.1 linux               # writes libs/linux/libsqlite3.a, which the build links
#   scripts/build-sqlite.sh 3.9.2 linux /tmp/sqlite.a  # an older version, as CI runs the tests on
#
# It needs curl, tar, sha256sum or shasum, and zig (https://ziglang.org), which compiles for both targets on any host:
# `zig` on the PATH, or the command in $ZIG, as ZIG="python3 -m ziglang" for the package of PyPI. CI uses zig 0.16.0,
# with which the same version and target give the same bytes on every host and in every directory.

set -euo pipefail

if [ $# -lt 2 ] || [ $# -gt 3 ]; then
    sed -n '3,13p' "$0" | sed 's/^# \{0,1\}//' >&2
    exit 2
fi

version=$1
target=$2
root=$(cd "$(dirname "$0")/.." && pwd)
output=${3:-$root/libs/$target/libsqlite3.a}
zig=${ZIG:-zig}

# The year of each version is part of its URL, and the SHA-256 of its sources is checked, so a version has to be
# listed here to be built: sqlite-autoconf-<number>.tar.gz, which sqlite.org has for every release, patch releases
# included.
case "$version" in
    3.51.1) year=2025; sha256=4f2445cd70479724d32ad015ec7fd37fbb6f6130013bd4bfbc80c32beb42b7e0 ;; # What the tests run on
    3.28.0) year=2019; sha256=d61b5286f062adfce5125eaf544d495300656908e61fca143517afcc0a89b7c3 ;; # Android API 30
    3.22.0) year=2018; sha256=2824ab1238b706bc66127320afbdffb096361130e23291f26928a027b885c612 ;; # Android API 28 and 29
    3.18.2) year=2017; sha256=0d40222ea818a559590c51994ac85570eff5c5f754cbe08c2d34a3791942c1ba ;; # Android API 26
    3.9.2)  year=2015; sha256=064c0abe9c9177534d4c770bca7a5902f9924b629ac886b4c08956be6dfbc36b ;; # Android API 24 and 25
    *)
        echo "Unknown SQLite version '$version': add its year and the SHA-256 of its sources to $0." >&2
        exit 2
        ;;
esac

# What Kotlin/Native links the tests for: glibc no newer than that of its sysroot on Linux, and MinGW-w64 on Windows.
# clang records the name of the object file in a COFF object, which zig compiles to a file with a random name, so the
# name is given, for the same bytes each time.
case "$target" in
    linux)   triple=x86_64-linux-gnu.2.17; extra="" ;;
    windows) triple=x86_64-windows-gnu;    extra="-Xclang -object-file-name=sqlite3.o" ;;
    *)
        echo "Unknown target '$target': linux or windows." >&2
        exit 2
        ;;
esac

# The full-text search modules, FTS3 and FTS4 with the enhanced query syntax, as the JVM's and Apple's SQLite have, and
# FTS5; R*Tree and Geopoly; the math functions; the dbstat virtual table; and column metadata. A version that lacks
# one ignores its option. -g0, as zig emits debug information by default, which holds the paths of the machine.
flags="-O2 -g0 -fPIC -DSQLITE_THREADSAFE=1 \
    -DSQLITE_ENABLE_FTS3 -DSQLITE_ENABLE_FTS3_PARENTHESIS -DSQLITE_ENABLE_FTS4 -DSQLITE_ENABLE_FTS5 \
    -DSQLITE_ENABLE_RTREE -DSQLITE_ENABLE_GEOPOLY -DSQLITE_ENABLE_MATH_FUNCTIONS \
    -DSQLITE_ENABLE_DBSTAT_VTAB -DSQLITE_ENABLE_COLUMN_METADATA"

# 3.51.1 -> 3510100
number=$(echo "$version" | awk -F. '{ printf "%d%02d%02d00", $1, $2, $3 }')
name=sqlite-autoconf-$number
url=https://www.sqlite.org/$year/$name.tar.gz

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

echo "Downloading $url"
curl -fsSL --retry 3 --retry-delay 5 -o "$work/$name.tar.gz" "$url"

if command -v sha256sum > /dev/null 2>&1; then
    actual=$(sha256sum "$work/$name.tar.gz" | cut -d ' ' -f 1)
else
    actual=$(shasum -a 256 "$work/$name.tar.gz" | cut -d ' ' -f 1)
fi
if [ "$actual" != "$sha256" ]; then
    echo "The SHA-256 of $url is $actual, not $sha256." >&2
    exit 1
fi

# Compiled in the directory of the sources, with relative paths, so that the host's kind of path doesn't matter
(
    cd "$work"
    tar -xzf "$name.tar.gz" "$name/sqlite3.c"
    cd "$name"
    echo "Compiling SQLite $version for $triple with zig $($zig version)"
    # shellcheck disable=SC2086
    $zig cc -target "$triple" -c sqlite3.c $flags $extra -o sqlite3.o
    $zig ar rcs libsqlite3.a sqlite3.o
)

mkdir -p "$(dirname "$output")"
cp "$work/$name/libsqlite3.a" "$output"
echo "Wrote $output"

#!/bin/sh
# Generates a bootstrap admin key (R1, plan §6) and prints it with its SHA-256 hash:
#   key=usk_<12 Base62>_<43 base64url>
#   hash=<64 lowercase hex>
# Set BOOTSTRAP_ADMIN_KEY_HASH to the hash and keep the key secret. Nothing is written to disk.
set -eu

prefix=""
while [ "${#prefix}" -lt 12 ]; do
    prefix="$prefix$(openssl rand -base64 48 | tr -dc 'A-Za-z0-9')"
done
prefix=$(printf '%s' "$prefix" | cut -c1-12)
secret=$(openssl rand 32 | openssl base64 -A | tr '+/' '-_' | tr -d '=')
key="usk_${prefix}_${secret}"
hash=$(printf '%s' "$key" | openssl dgst -sha256 | awk '{print $NF}')

printf 'key=%s\nhash=%s\n' "$key" "$hash"

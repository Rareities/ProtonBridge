#!/bin/bash
set -euo pipefail

if [ "$#" -ne 2 ]; then
    echo "Only pass the amount of commits and one translation file!"
    exit 1
fi

if [[ ! "$1" =~ ^[1-9][0-9]*$ ]] || [[ ! "$2" =~ ^app/src/main/res/values-[A-Za-z0-9+_-]+/strings\.xml$ ]]; then
    echo "Expected a positive commit count and one Android translation resource path" >&2
    exit 1
fi
RAW_DIFF=$(git diff -U0 "HEAD~$1" -- "$2")
DIFF=$(printf '%s\n' "$RAW_DIFF" | sed -n '/^+++ /d; s/^+//p' | sed 's/^[ \t]*//')
printf '<xml>%s</xml>\n' "$DIFF" | xmlstarlet sel -t -m '//string' -v . -n > changed_texts.txt
TRANSLATIONS=$(cat changed_texts.txt)


TRANSLATIONS="${TRANSLATIONS//$'\n'/' ; '}"
TRANSLATIONS="${TRANSLATIONS//$'\r'/' ; '}"

# First, replace all string-substitutions
TRANSLATIONS=$(printf '%s\n' "$TRANSLATIONS" | sed 's/%1$s//g; s/%d//g; s/%s//g; s/"//g')
printf '%s\n' "$TRANSLATIONS"

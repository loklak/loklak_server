#!/usr/bin/env bash

# This script contains only functions that can be used in other bash scripts

function change_shortlink_urlstub() {
    # changes port_number in shortlink.urlstub=http://localhost:port_number
    sed -i 's/\(shortlink\.urlstub=http:.*:\)\(.*\)/\1'"$1"'/' conf/config.properties
}


#!/usr/bin/env bash

set -e
# import in shellcheck / CI / IntelliJ compatible ways
# shellcheck source=/dev/null
source "${BASH_SOURCE%/*}/test.inc.sh" || source test.inc.sh

cromwell::build::setup_everyone_environment

cromwell::build::setup_centaur_environment

cromwell::build::assemble_jars

centaur/test_cromwell.sh \
    -j "${CROMWELL_BUILD_JAR}" \
    -c "${CROMWELL_BUILD_SCRIPTS_RESOURCES}/local_application.conf" \
    -g

cromwell::build::generate_code_coverage

SHELL := /bin/bash
.SILENT:
.PHONY: git-has-pushed git-is-clean
all:
	mvn -q -U dependency:build-classpath compile -DincludeScope=runtime -Dmdep.outputFile=target/.classpath -Dmaven.compiler.debug=false

install:
	mvn -q install

test:
	mvn -q -Dsurefire.useFile=false test

clean:
	mvn -q clean

package:
	. ~/.distelli.config && mvn -q -DincludeScope=runtime dependency:copy-dependencies package

show-deps:
	mvn dependency:tree

#git-has-pushed:
#	! git diff --stat HEAD origin/master | grep . >/dev/null && [ 0 == $${PIPESTATUS[0]} ]

git-is-clean:
	git diff-index --quiet HEAD --

git-is-master:
	[ master = "$$(git rev-parse --abbrev-ref HEAD)" ]

publish: git-is-clean git-is-master
	if [ -z "$(NEW_VERSION)" ]; then echo 'Please run `make publish NEW_VERSION=1.1`' 1>&2; false; fi
	. ~/.distelli.config && mvn -DdevelopmentVersion=$(NEW_VERSION) --batch-mode -DautoVersionSubmodules=true -Dsurefire.useFile=false -DgenerateBackupPoms=false -DuseReleaseProfile=false -DscmCommentPrefix='[skip ci][release:prepare]' release:prepare release:perform $(MVN_OPTS)

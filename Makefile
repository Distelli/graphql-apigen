PACKAGE_NAME=graphql-apigen-pom
SHELL := /bin/bash
.SILENT:
.PHONY: git-pull-needed git-is-clean git-is-master
all:
	mvn -q -U dependency:build-classpath compile -DincludeScope=runtime -Dmdep.outputFile=target/.classpath -Dmaven.compiler.debug=false

install:
	mvn -q install

test:
	mvn -q -Dsurefire.useFile=false test

clean:
	mvn -q clean

package:
	mvn -q -DincludeScope=runtime dependency:copy-dependencies package

show-deps:
	mvn dependency:tree

git-pull-needed:
	git remote update
	[ $$(git rev-parse '@{u}') = $$(git merge-base '@' '@{u}') ]

git-is-clean:
	git diff-index --quiet HEAD --

git-is-master:
	[ master = "$$(git rev-parse --abbrev-ref HEAD)" ]

NEXT_SNAPSHOT=$$(echo $(NEW_VERSION) | awk -F. '{OFS=".";$$NF=$$(NF)+1;print $$0}')-SNAPSHOT

publish: git-is-clean git-is-master git-pull-needed
	if [ -z "$(NEW_VERSION)" ]; then echo 'Please run `make publish NEW_VERSION=1.1`' 1>&2; false; fi
	mvn versions:set -DgenerateBackupPoms=false -DnewVersion=$(NEW_VERSION) && \
		sed -i '' 's!<apigen\.version>.*</apigen\.version>!<apigen.version>'$(NEW_VERSION)'</apigen.version>!' apigen/src/test/projects/*/pom.xml && \
		git commit -am '[skip ci][release:prepare] prepare release $(PACKAGE_NAME)-$(NEW_VERSION)' && \
		git tag -m 'Preparing new release $(PACKAGE_NAME)-$(NEW_VERSION)' -a '$(PACKAGE_NAME)-$(NEW_VERSION)' && \
		mvn clean test deploy -Prelease && \
		mvn versions:set -DgenerateBackupPoms=false -DnewVersion=$(NEXT_SNAPSHOT) && \
		sed -i '' 's!<apigen\.version>.*</apigen\.version>!<apigen.version>'$(NEXT_SNAPSHOT)'</apigen.version>!' apigen/src/test/projects/*/pom.xml && \
		git commit -am '[skip ci][release:perform] prepare for next development iteration' && \
		git push --follow-tags

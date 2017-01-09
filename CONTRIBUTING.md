# How to contribute

## Making Changes

* Fork the repository on GitHub
* Create a topic branch from where you want to base your work.
  * This is usually the master branch.
  * Only target release branches if you are certain your fix must be on that
    branch.
  * To quickly create a topic branch based on master; `git checkout -b
    fix/master/my_contribution master`. Please avoid working directly on the
    `master` branch.
* Make commits of logical units.
* Check for unnecessary whitespace with `git diff --check` before committing.
* Make sure you have added the necessary tests for your changes.
* Run _all_ the tests to assure nothing else was accidentally broken.
* Be sure to update the documentation.

## Submitting Changes

* Sign the [Contributor License Agreement](https://www.clahub.com/agreements/Distelli/graphql-apigen).
* Push your changes to a topic branch in your fork of the repository.
* Submit a pull request to the repository in the Distelli organization.

## Revert Policy

* We reserve the right to revert commits for any reason, although this is rare.


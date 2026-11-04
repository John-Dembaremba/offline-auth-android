.PHONY: all format format-check lint test android-test android-test-compile build clean setup-precommit precommit

all: format lint test android-test-compile build

format:
	./gradlew spotlessApply

format-check:
	./gradlew spotlessCheck

lint:
	./gradlew :offlineauth:lintDebug

test:
	./gradlew :offlineauth:testDebugUnitTest

android-test-compile:
	./gradlew :offlineauth:compileDebugAndroidTestKotlin

android-test:
	./gradlew :offlineauth:connectedDebugAndroidTest

build:
	./gradlew :offlineauth:assembleDebug

clean:
	./gradlew clean

setup-precommit:
	python -m pip install pre-commit
	pre-commit install

precommit:
	pre-commit run --all-files

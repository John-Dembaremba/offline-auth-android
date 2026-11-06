.PHONY: all \
	format \
	format-check \
	lint \
	test \
	android-test \
	android-test-compile \
	android-performance-test \
	android-parallelism-test \
	android-memory-test \
	android-stress-test \
	android-mixed-failure-test \
	android-full-test \
	build \
	clean \
	setup-precommit \
	precommit

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
	./gradlew :offlineauth:connectedDebugAndroidTest \
		-Pandroid.testInstrumentationRunnerArguments.notClass="io.github.johndembaremba.offlineauth.OfflineAuthPerformanceTest,io.github.johndembaremba.offlineauth.OfflineAuthParallelismPerformanceTest,io.github.johndembaremba.offlineauth.OfflineAuthMemoryPerformanceTest,io.github.johndembaremba.offlineauth.OfflineAuthStressTest,io.github.johndembaremba.offlineauth.OfflineAuthMixedFailureStressTest"

android-performance-test:
	./gradlew :offlineauth:connectedDebugAndroidTest \
		-Pandroid.testInstrumentationRunnerArguments.class=io.github.johndembaremba.offlineauth.OfflineAuthPerformanceTest

android-parallelism-test:
	./gradlew :offlineauth:connectedDebugAndroidTest \
		-Pandroid.testInstrumentationRunnerArguments.class=io.github.johndembaremba.offlineauth.OfflineAuthParallelismPerformanceTest

android-memory-test:
	./gradlew :offlineauth:connectedDebugAndroidTest \
		-Pandroid.testInstrumentationRunnerArguments.class=io.github.johndembaremba.offlineauth.OfflineAuthMemoryPerformanceTest

android-stress-test:
	./gradlew :offlineauth:connectedDebugAndroidTest \
		-Pandroid.testInstrumentationRunnerArguments.class=io.github.johndembaremba.offlineauth.OfflineAuthStressTest

android-mixed-failure-test:
	./gradlew :offlineauth:connectedDebugAndroidTest \
		-Pandroid.testInstrumentationRunnerArguments.class=io.github.johndembaremba.offlineauth.OfflineAuthMixedFailureStressTest

android-full-test:
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

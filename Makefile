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
	android-heavy-test \
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

#
# Standard Android compatibility/security suite.
#
# Heavy performance, memory, and stress tests are intentionally excluded.
#
# AndroidJUnitRunner supports a comma-separated list for the "class"
# instrumentation argument, so we explicitly select the normal tests instead
# of trying to exclude multiple heavy test classes with "notClass".
#
android-test:
	./gradlew :offlineauth:connectedDebugAndroidTest \
		-Pandroid.testInstrumentationRunnerArguments.class="io.github.johndembaremba.offlineauth.OfflineAuthIntegrationTest,io.github.johndembaremba.offlineauth.OfflineAuthSecurityFailureTest,io.github.johndembaremba.offlineauth.OfflineAuthStorageSecurityTest,io.github.johndembaremba.offlineauth.crypto.EnvelopeDecryptorTest,io.github.johndembaremba.offlineauth.crypto.KeyStoreManagerTest,io.github.johndembaremba.offlineauth.serialization.CredentialParserTest,io.github.johndembaremba.offlineauth.storage.EncryptedCredentialStoreTest,io.github.johndembaremba.offlineauth.validation.CredentialValidatorTest,io.github.johndembaremba.offlineauth.verification.Pbkdf2Sha256PasswordVerifierTest"

#
# Dedicated performance benchmark.
#
android-performance-test:
	./gradlew :offlineauth:connectedDebugAndroidTest \
		-Pandroid.testInstrumentationRunnerArguments.class=io.github.johndembaremba.offlineauth.OfflineAuthPerformanceTest

#
# Dedicated parallelism benchmark.
#
android-parallelism-test:
	./gradlew :offlineauth:connectedDebugAndroidTest \
		-Pandroid.testInstrumentationRunnerArguments.class=io.github.johndembaremba.offlineauth.OfflineAuthParallelismPerformanceTest

#
# Dedicated memory benchmark.
#
android-memory-test:
	./gradlew :offlineauth:connectedDebugAndroidTest \
		-Pandroid.testInstrumentationRunnerArguments.class=io.github.johndembaremba.offlineauth.OfflineAuthMemoryPerformanceTest

#
# Long-running repeated-sync stress test.
#
android-stress-test:
	./gradlew :offlineauth:connectedDebugAndroidTest \
		-Pandroid.testInstrumentationRunnerArguments.class=io.github.johndembaremba.offlineauth.OfflineAuthStressTest

#
# Mixed success/failure/rollback stress test.
#
android-mixed-failure-test:
	./gradlew :offlineauth:connectedDebugAndroidTest \
		-Pandroid.testInstrumentationRunnerArguments.class=io.github.johndembaremba.offlineauth.OfflineAuthMixedFailureStressTest

#
# Run all heavy performance/stress suites.
#
android-heavy-test:
	$(MAKE) android-performance-test
	$(MAKE) android-parallelism-test
	$(MAKE) android-memory-test
	$(MAKE) android-stress-test
	$(MAKE) android-mixed-failure-test

#
# Everything, including heavy tests.
#
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

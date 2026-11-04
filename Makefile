.PHONY: all format format-check lint test build clean setup-precommit precommit

all: format-check lint test build

format:
	./gradlew spotlessApply

format-check:
	./gradlew spotlessCheck

lint:
	./gradlew :offlineauth:lintDebug

test:
	./gradlew :offlineauth:testDebugUnitTest

build:
	./gradlew :offlineauth:assembleDebug

clean:
	./gradlew clean

setup-precommit:
	python -m pip install pre-commit
	pre-commit install

precommit:
	pre-commit run --all-files

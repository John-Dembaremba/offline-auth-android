.PHONY: all lint test build clean

all: lint test build

lint:
	./gradlew :offlineauth:lintDebug

test:
	./gradlew :offlineauth:testDebugUnitTest

build:
	./gradlew :offlineauth:assembleDebug

clean:
	./gradlew clean
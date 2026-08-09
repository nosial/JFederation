.PHONY: all build test clean publish

JAR_NAME = jfederation
JAR_PATH = target/$(JAR_NAME).jar

all: build

build:
	mvn -B package -DskipTests

test:
	mvn -B test

clean:
	mvn -B clean

publish:
	mvn -B -Prelease deploy -DskipTests
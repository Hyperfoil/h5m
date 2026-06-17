.PHONY: dev-cli

dev-cli:
	mvn clean package -Pcli -Dh5m.cli.native=false -DskipTests
	cp target/cli/h5m.jar .
	mvn clean quarkus:dev -DskipTests

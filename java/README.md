Tuesday, 20 August 2024, 16:56

build:
mvn clean package

run:
java -jar ./target/get-gcp-id-token-20250606.jar --creds YOUR_KEY_FILE.json --audience FOO

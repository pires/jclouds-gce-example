jclouds-gce-example
============

## Build ##

```
mvn clean package
```

## Usage ##

### Retrieving credentials ###

As ripped from [jclouds-labs-google instructions](https://github.com/jclouds/jclouds-labs-google/blob/master/google-compute-engine/README.txt)
```
Authenticating into the instances:
--------

GCE uses exclusively ssh keys to login into instances.
In order for an instance to be sshable a public key must be installed. Public keys are installed if they are present in the project or instance's metatada.

For an instance to be ssable one of the following must happen:
1 - the project's metadata has an adequately built "sshKeys" entry and a corresponding private key is provided in GoogleComputeEngineTemplateOptions when createNodesInGroup is called.
2 - an instance of GoogleComputeEngineTemplateOptions with an adequate public and private key is provided.

NOTE: if methods 2 is chosen the global project keys will not be installed in the instance.

Please refer to Google's documentation on how to form valid project wide ssh keys metadata entries.

FAQ:
--------

* Q. What is the identity for GCE?

A. the identity is the developer email which can be obtained from the admin GUI. Its usually something in the form: <my account id>@developer.gserviceaccount.com

* Q. What is the credential for GCE

A. the credential is a private key, in pem format. It can be extracted from the p12 keystore that is obtained when creating a "Service Account" (in the GUI: Google apis console > Api Access > Create another client ID > "Service Account"

* Q. How to convert a p12 keystore into a pem format jclouds Google Compute Engine can handle:

A.

1. Convert the p12 file into pem format (it will ask for the keystore password, which is usually "notasecret"):
 openssl pkcs12 -in <my_keystore>.p12 -out <my_keystore>.pem -nodes

2. Extract only the pk and remove passphrase
 openssl rsa -in <my_keystore>.pem -out <my_key>.pem

The last file (<my_key>.pem) should contain the private-key needed for you to use this example code.

```

### Listing options and examples ###

```
java -jar target/jclouds-gce-example-0.1-SNAPSHOT-jar-with-dependencies.jar --help
```

### Listing nodes ###

```
java -jar target/jclouds-gce-example-0.1-SNAPSHOT-jar-with-dependencies.jar --account <your_account_id>@developer.gserviceaccount.com --pk <your_key>.pem listnodes
```

### Adding node ###

Node ID will be generated, as I didn't have the time to discover how to define it programatically.

```
java -jar target/jclouds-gce-example-0.1-SNAPSHOT-jar-with-dependencies.jar --account <your_account_id>@developer.gserviceaccount.com --pk <your_key>.pem add default
```

### Destroying node ###

Node ID is a composite-key that's represented as <zone_id>/<node_name>. One example is _europe-west1-a/instance-1_.

```
java -jar target/jclouds-gce-example-0.1-SNAPSHOT-jar-with-dependencies.jar --account <your_account_id>@developer.gserviceaccount.com --pk <your_key>.pem destroy default zoneid/nodeid
```

### Listing images ###

```
java -jar target/jclouds-gce-example-0.1-SNAPSHOT-jar-with-dependencies.jar --account <your_account_id>@developer.gserviceaccount.com --pk <your_key>.pem listimages
```
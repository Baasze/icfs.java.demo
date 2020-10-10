# icfs.java.demo

run icfs.java.demon

```sh
gradle run
```

or run test

```sh
gradle test
```

## Get icfs.jar and dependency

[java-icfs](http://icfs.baasze.com:5002/ipns/bafzm3jqbec7ulhfmm7s7ydt2mf32nbsjy4237mvzj5skzbkxrfxz7axghsyum/java-icfs)

## Quick start

### import jar

copy `icfs.jar`、`cid.jar`、`multibase.jar`、`multiaddr.jar` and `multihash.jar` into libs

### config gradle

```gradle
dependencies {
    compile fileTree(dir:'libs',includes:['*jar'])
    implementation fileTree(dir:'libs',includes:['*jar'])

    compile 'com.squareup.retrofit2:converter-gson:2.5.0'
    compile 'com.squareup.okhttp3:logging-interceptor:3.12.1'
    ...
}
```

### Use

use Clusetr client store file and ICFS client cat、get file. see [App](./src/main/java/icfs/java/demo/App.java)

```java
import icfs.api.*;          // icfs client
import icfs.cluster.api.*;  // cluster client
import icfs.utils.*;        // NamedStreamable、JSONParser
import icfs.cid.*;

public class App {
  public static void main(String[] args) {
    try {
      ICFS icfs = new ICFS("/dns4/xxx.xxx.xxx/tcp/5001/http"); // or /ip4/127.0.0.1/tcp/5001/
      ICFSCluster icfsCluster = new ICFSCluster("/dns4/xxx.xxx.xxx/tcp/9094/http");
      NamedStreamable.ByteArrayWrapper content = new NamedStreamable.ByteArrayWrapper("hello.txt",
              "ICFS Cluster is awsome!".getBytes());
      String cid = icfsCluster.add(content).get(0).cid;     // cluster client store file
      System.out.println(cid);
      byte[] catResult = icfs.cat(Cid.decode(cid));         // icfs client cat file
      byte[] getResult = icfs.get(Cid.decode(cid));         // icfs client get file
      System.out.println(new String(catResult));
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
```

## Details

### icfs add

```java
// icfs add content
NamedStreamable.ByteArrayWrapper content = new NamedStreamable.ByteArrayWrapper("hello.txt",
        "ICFS is awsome!".getBytes());
String cid = icfs.add(content).get(0).cid; // return List<MerkleNode>
System.out.println("icfs add content: " + cid);

// icfs add file
File f = new File(System.getProperty("user.dir") + "/src/test/java/icfs/demo/api/APITest.java");
InputStream in = new FileInputStream(f);
NamedStreamable.InputStreamWrapper file = new NamedStreamable.InputStreamWrapper(in);
List<MerkleNode> fileResult = icfs.add(file);
System.out.println("icfs add a file:" + new Gson().toJson(fileResult) + "\n");

// icfs add
File d = new File(System.getProperty("user.dir") + "/src/test/java/icfs/demo/api");
NamedStreamable.FileWrapper dir = new NamedStreamable.FileWrapper(d);
System.out.println("icfs add a dir:" + new Gson().toJson(icfs.add(dir)) + "\n");

```

### icfs dag

```java
String original = "{\"data\":1234}";
byte[] object = original.getBytes();
MerkleNode put = icfs.dag.put("json", object);

String result = put.hash.toString();
System.out.println("dag put cid: " + result);

byte[] get = icfs.dag.get(expected);
System.out.println("dag get : " + (new String(get).trim()));
```

### icfs key

```java
List<KeyInfo> existing = icfs.key.list();
String name = "mykey" + System.nanoTime();
KeyInfo gen = icfs.key.gen(name, Optional.of("sm2"), Optional.of("-1"));
String newName = "bob" + System.nanoTime();
Object rename = icfs.key.rename(name, newName);
List<KeyInfo> rm = icfs.key.rm(newName);
List<KeyInfo> remaining = icfs.key.list();
```

### icfs pin

```java
MerkleNode file = icfs.add(new NamedStreamable.ByteArrayWrapper("some data".getBytes())).get(0);
Multihash hash = file.hash;
Map<Multihash, Object> ls1 = icfs.pin.ls(ICFS.PinType.all);
boolean pinned = ls1.containsKey(hash);
// need import icfs.multihash.Multihash;
List<Multihash> rm = icfs.pin.rm(hash);
// second rm should not throw a http 500, but return an empty list
//            List<Multihash> rm2 = icfs.pin.rm(hash);
List<Multihash> add2 = icfs.pin.add(hash);
// adding something already pinned should succeed
List<Multihash> add3 = icfs.pin.add(hash);
Map<Multihash, Object> ls = icfs.pin.ls(ICFS.PinType.recursive);
icfs.repo.gc();
// object should still be present after gc
Map<Multihash, Object> ls2 = icfs.pin.ls(ICFS.PinType.recursive);
boolean stillPinned = ls2.containsKey(hash);
```

### icfs name

```java
// JSON document
String json = "{\"name\":\"blogpost\",\"documents\":[]}";

// Add a DAG node to ICFS
MerkleNode merkleNode = icfs.dag.put("json", json.getBytes());

// Get a DAG node
byte[] res = icfs.dag.get((Cid) merkleNode.hash);
System.out.println("dag get: " + JSONParser.parse(new String(res)));

// Publish to IPNS
Map result = icfs.name.publish(merkleNode.hash, Optional.of("mytestkey"));
}
```

### icfs pubsub

pub

```java
String topic = "topic" + System.nanoTime();
Stream<Map<String, Object>> sub = icfs.pubsub.sub(topic);
String data = "Hello!";
Object pub = icfs.pubsub.pub(topic, data);
Object pub2 = icfs.pubsub.pub(topic, "G'day");
List<Map> results = sub.limit(2).collect(Collectors.toList());
Assert.assertTrue( ! results.get(0).equals(Collections.emptyMap()));
```

sub

```java
String topic = "topic" + System.nanoTime();
List<Map<String, Object>> res = Collections.synchronizedList(new ArrayList<>());
new Thread(() -> {
    try {
        icfs.pubsub.sub(topic, res::add, t -> t.printStackTrace());
    } catch (IOException e) {
        throw new RuntimeException(e);}
}).start();

int nMessages = 100;
for (int i = 1; i < nMessages; ) {
    icfs.pubsub.pub(topic, "Hello!");
    if (res.size() >= i) {
        i++;
    }
}
Assert.assertTrue(res.size() > nMessages - 5); // pubsub is not reliable so it loses messages
```

### icfs swarm

```java
// need import icfs.multiaddr.MultiAddress;
Map<Cid, List<MultiAddress>> addrs = icfs.swarm.addrs();
Cid selfId = Cid.decode(icfs.id().get("ID").toString());
if (addrs.size() > 0) {
    boolean contacted = addrs.entrySet().stream()
            .anyMatch(e -> {
                Cid target = e.getKey();
                List<MultiAddress> nodeAddrs = e.getValue();
                boolean contactable = nodeAddrs.stream()
                        .anyMatch(addr -> {
                            try {
                                if (target.equals(selfId)) {
                                    return true;
                                }
                                MultiAddress peer = new MultiAddress(addr.toString() + "/p2p/" + target.toString());
                                System.out.println(addr.toString() + "/p2p/" + target.toString());
                                Map connect = icfs.swarm.connect(peer);
                                Map disconnect = icfs.swarm.disconnect(peer);
                                return true;
                            } catch (Exception ex) {
                                return false;
                            }
                        });
                try {
                    System.out.printf("target cid: %s", target.toString());
                    Map id = icfs.id(target);
                    Map ping = icfs.ping(target);
                    return contactable;
                } catch (Exception ex) {
                    // not all nodes have to be contactable
                    return false;
                }
            });
    if (!contacted)
        throw new IllegalStateException("Couldn't contact any node!");
}
List<Peer> peers = icfs.swarm.peers();
System.out.println(peers);
```

### icfs bootstrap

```java
List<MultiAddress> bootstrap = icfs.bootstrap.list();
System.out.println(bootstrap);
List<MultiAddress> rm = icfs.bootstrap.rm(bootstrap.get(0), false);
List<MultiAddress> add = icfs.bootstrap.add(bootstrap.get(0));
System.out.println();
```

### icfs-cluster client

see [test demo](./src/test/java/icfs/demo/cluster/api/ClusterTest.java)

```java
icfsCluster.id();
icfsCluster.version();
icfsCluster.add(file);
icfsCluster.pins.ls();
icfsCluster.pins.ls(String CID);
icfsCluster.pins.add(String CID);
icfsCluster.pins.rm(String CID);
icfsCluster.peers.ls();
icfsCluster.peers.rm(String CID);
icfsCluster.sync.sync();
icfsCluster.sync.sync(String CID);
icfsCluster.recover.recover();
icfsCluster.recover.recover(String CID);
icfsCluster.allocation.ls();
icfsCluster.allocation(String CID);
icfsCluster.health.graph();
```

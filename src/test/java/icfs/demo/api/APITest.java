package icfs.demo.api;

import icfs.api.*;
import icfs.api.cbor.*;
import icfs.cid.*;
import icfs.multihash.Multihash;
import icfs.multiaddr.MultiAddress;
import icfs.utils.*;
import org.junit.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;
import java.util.stream.*;

import static org.junit.Assert.assertTrue;

public class APITest {

    private final ICFS icfs = new ICFS(new MultiAddress("/dns4/xxx.xxx.xxx/tcp/5001/http"));
    private final Random r = new Random(33550336); // perfect

    @Test
    public void dag() throws IOException {
        String original = "{\"data\":1234}";
        byte[] object = original.getBytes();
        MerkleNode put = icfs.dag.put("json", object);

        Cid expected = Cid.decode("bafy43jqbecmlxrqlvim7lv3hlakbtp2472cz752t4hedm5unuzalr6amxyis4");

        Multihash result = put.hash;
        Assert.assertTrue("Correct cid returned", result.equals(expected));

        byte[] get = icfs.dag.get(expected);
        Assert.assertTrue("Raw data equal", original.equals(new String(get).trim()));
    }

    @Test
    public void dagCbor() throws IOException {
        Map<String, CborObject> tmp = new LinkedHashMap<>();
        String value = "G'day mate!";
        tmp.put("data", new CborObject.CborString(value));
        CborObject original = CborObject.CborMap.build(tmp);
        byte[] object = original.toByteArray();
        MerkleNode put = icfs.dag.put("cbor", object);

        Cid cid = (Cid) put.hash;

        byte[] get = icfs.dag.get(cid);
        Assert.assertTrue("Raw data equal", ((Map)JSONParser.parse(new String(get))).get("data").equals(value));

        Cid expected = Cid.decode("bafy43jqbebq7fo6uvyztzbhjsksekp24gc2fgsfxq4pxedaytid2ttngrk7hg");
        Assert.assertTrue("Correct cid returned", cid.equals(expected));
    }

    @Test
    public void keys() throws IOException {
        List<KeyInfo> existing = icfs.key.list();
        String name = "mykey" + System.nanoTime();
        KeyInfo gen = icfs.key.gen(name, Optional.of("sm2"), Optional.of("-1"));
        String newName = "bob" + System.nanoTime();
        Object rename = icfs.key.rename(name, newName);
        List<KeyInfo> rm = icfs.key.rm(newName);
        List<KeyInfo> remaining = icfs.key.list();
        Assert.assertTrue("removed key", remaining.equals(existing));
    }

    @Test
    public void ipldNode() {
        Function<Stream<Pair<String, CborObject>>, CborObject.CborMap> map =
                s -> CborObject.CborMap.build(s.collect(Collectors.toMap(p -> p.left, p -> p.right)));
        CborObject.CborMap a = map.apply(Stream.of(new Pair<>("b", new CborObject.CborLong(1))));

        CborObject.CborMap cbor = map.apply(Stream.of(new Pair<>("a", a), new Pair<>("c", new CborObject.CborLong(2))));

        IpldNode.CborIpldNode node = new IpldNode.CborIpldNode(cbor);
        List<String> tree = node.tree("", -1);
        Assert.assertTrue("Correct tree", tree.equals(Arrays.asList("/a/b", "/c")));
    }

    @Test
    public void singleFileTest() throws IOException {
        NamedStreamable.ByteArrayWrapper file = new NamedStreamable.ByteArrayWrapper("hello.txt", "G'day world! ICFS rocks!".getBytes());
        fileTest(file);
    }

    @Test
    public void wrappedSingleFileTest() throws IOException {
        NamedStreamable.ByteArrayWrapper file = new NamedStreamable.ByteArrayWrapper("hello.txt", "G'day world! ICFS rocks!".getBytes());
        List<MerkleNode> addParts = icfs.add(file, true);
        MerkleNode filePart = addParts.get(0);
        MerkleNode dirPart = addParts.get(1);
        byte[] catResult = icfs.cat(filePart.hash);
        byte[] getResult = icfs.get(filePart.hash);
        if (!Arrays.equals(catResult, file.getContents()))
            throw new IllegalStateException("Different contents!");
        List<Multihash> pinRm = icfs.pin.rm(dirPart.hash, true);
        if (!pinRm.get(0).equals(dirPart.hash))
            throw new IllegalStateException("Didn't remove file!");
        Object gc = icfs.repo.gc();
    }

    @Test
    public void dirTest() throws IOException {
        Path test = Files.createTempDirectory("test");
        Files.write(test.resolve("file.txt"), "G'day ICFS!".getBytes());
        NamedStreamable dir = new NamedStreamable.FileWrapper(test.toFile());
        List<MerkleNode> add = icfs.add(dir);
        MerkleNode addResult = add.get(add.size() - 1);
        List<MerkleNode> ls = icfs.ls(addResult.hash);
        Assert.assertTrue(ls.size() > 0);
    }

    @Test
    public void directoryTest() throws IOException {
        Random rnd = new Random();
        String dirName = "folder" + rnd.nextInt(100);
        Path tmpDir = Files.createTempDirectory(dirName);

        String fileName = "afile" + rnd.nextInt(100);
        Path file = tmpDir.resolve(fileName);
        FileOutputStream fout = new FileOutputStream(file.toFile());
        byte[] fileContents = "ICFS rocks!".getBytes();
        fout.write(fileContents);
        fout.flush();
        fout.close();

        String subdirName = "subdir";
        tmpDir.resolve(subdirName).toFile().mkdir();

        String subfileName = "subdirfile" + rnd.nextInt(100);
        Path subdirfile = tmpDir.resolve(subdirName + "/" + subfileName);
        FileOutputStream fout2 = new FileOutputStream(subdirfile.toFile());
        byte[] file2Contents = "ICFS still rocks!".getBytes();
        fout2.write(file2Contents);
        fout2.flush();
        fout2.close();

        List<MerkleNode> addParts = icfs.add(new NamedStreamable.FileWrapper(tmpDir.toFile()));
        MerkleNode addResult = addParts.get(addParts.size() - 1);
        List<MerkleNode> lsResult = icfs.ls(addResult.hash);
        if (lsResult.size() != 2)
            throw new IllegalStateException("Incorrect number of objects in ls!");
        if (! lsResult.stream().map(x -> x.name.get()).collect(Collectors.toSet()).equals(new HashSet<>(Arrays.asList(subdirName, fileName))))
            throw new IllegalStateException("Dir not returned in ls!");
        byte[] catResult = icfs.cat(addResult.hash, "/" + fileName);
        if (! Arrays.equals(catResult, fileContents))
            throw new IllegalStateException("Different contents!");

        byte[] catResult2 = icfs.cat(addResult.hash, "/" + subdirName + "/" + subfileName);
        if (! Arrays.equals(catResult2, file2Contents))
            throw new IllegalStateException("Different contents!");
    }

//    @Test
    public void largeFileTest() throws IOException {
        byte[] largerData = new byte[100*1024*1024];
        new Random(1).nextBytes(largerData);
        NamedStreamable.ByteArrayWrapper largeFile = new NamedStreamable.ByteArrayWrapper("nontrivial.txt", largerData);
        fileTest(largeFile);
    }

//    @Test
    public void hugeFileStreamTest() throws IOException {
        byte[] hugeData = new byte[1000*1024*1024];
        new Random(1).nextBytes(hugeData);
        NamedStreamable.ByteArrayWrapper largeFile = new NamedStreamable.ByteArrayWrapper("massive.txt", hugeData);
        MerkleNode addResult = icfs.add(largeFile).get(0);
        InputStream in = icfs.catStream(addResult.hash);

        byte[] res = new byte[hugeData.length];
        int offset = 0;
        byte[] buf = new byte[4096];
        int r;
        while ((r = in.read(buf)) >= 0) {
            try {
                System.arraycopy(buf, 0, res, offset, r);
                offset += r;
            }catch (Exception e){
                e.printStackTrace();
            }
        }
        if (!Arrays.equals(res, hugeData))
            throw new IllegalStateException("Different contents!");
    }

    @Test
    public void hostFileTest() throws IOException {
        Path tempFile = Files.createTempFile("ICFS", "tmp");
        BufferedWriter w = new BufferedWriter(new FileWriter(tempFile.toFile()));
        w.append("Some data");
        w.flush();
        w.close();
        NamedStreamable hostFile = new NamedStreamable.FileWrapper(tempFile.toFile());
        fileTest(hostFile);
    }

    @Test
    public void hashOnly() throws IOException {
        byte[] data = randomBytes(4096);
        NamedStreamable file = new NamedStreamable.ByteArrayWrapper(data);
        MerkleNode addResult = icfs.add(file, false, true).get(0);
        List<Multihash> local = icfs.refs.local();
        if (local.contains(addResult.hash))
            throw new IllegalStateException("Object shouldn't be present!");
    }

    public void fileTest(NamedStreamable file)  throws IOException{
        MerkleNode addResult = icfs.add(file).get(0);
        byte[] catResult = icfs.cat(addResult.hash);
        byte[] getResult = icfs.get(addResult.hash);
        if (!Arrays.equals(catResult, file.getContents()))
            throw new IllegalStateException("Different contents!");
        List<Multihash> pinRm = icfs.pin.rm(addResult.hash, true);
        if (!pinRm.get(0).equals(addResult.hash))
            throw new IllegalStateException("Didn't remove file!");
        Object gc = icfs.repo.gc();
    }

    @Test
    public void pinTest() throws IOException {
        MerkleNode file = icfs.add(new NamedStreamable.ByteArrayWrapper("some data".getBytes())).get(0);
        Multihash hash = file.hash;
        Map<Multihash, Object> ls1 = icfs.pin.ls(ICFS.PinType.all);
        boolean pinned = ls1.containsKey(hash);
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
        Assert.assertTrue("Pinning works", pinned && stillPinned);
    }

    @Test
    public void pinUpdate() throws IOException {
        MerkleNode child1 = icfs.add(new NamedStreamable.ByteArrayWrapper("some data".getBytes())).get(0);
        Multihash hashChild1 = child1.hash;
        System.out.println("child1: " + hashChild1);

        CborObject.CborMerkleLink root1 = new CborObject.CborMerkleLink(hashChild1);
        MerkleNode root1Res = icfs.block.put(Collections.singletonList(root1.toByteArray()), Optional.of("cbor")).get(0);
        System.out.println("root1: " + root1Res.hash);
        icfs.pin.add(root1Res.hash);

        CborObject.CborList root2 = new CborObject.CborList(Arrays.asList(new CborObject.CborMerkleLink(hashChild1), new CborObject.CborLong(42)));
        MerkleNode root2Res = icfs.block.put(Collections.singletonList(root2.toByteArray()), Optional.of("cbor")).get(0);
        List<Multihash> update = icfs.pin.update(root1Res.hash, root2Res.hash, true);

        Map<Multihash, Object> ls = icfs.pin.ls(ICFS.PinType.all);
        boolean childPresent = ls.containsKey(hashChild1);
        if (!childPresent)
            throw new IllegalStateException("Child not present!");

        icfs.repo.gc();
        Map<Multihash, Object> ls2 = icfs.pin.ls(ICFS.PinType.all);
        boolean childPresentAfterGC = ls2.containsKey(hashChild1);
        if (!childPresentAfterGC)
            throw new IllegalStateException("Child not present!");
    }

    @Test
    public void rawLeafNodePinUpdate() throws IOException {
        MerkleNode child1 = icfs.block.put("some data".getBytes(), Optional.of("raw"));
        Multihash hashChild1 = child1.hash;
        System.out.println("child1: " + hashChild1);

        CborObject.CborMerkleLink root1 = new CborObject.CborMerkleLink(hashChild1);
        MerkleNode root1Res = icfs.block.put(Collections.singletonList(root1.toByteArray()), Optional.of("cbor")).get(0);
        System.out.println("root1: " + root1Res.hash);
        icfs.pin.add(root1Res.hash);

        MerkleNode child2 = icfs.block.put("G'day new tree".getBytes(), Optional.of("raw"));
        Multihash hashChild2 = child2.hash;

        CborObject.CborList root2 = new CborObject.CborList(Arrays.asList(
                new CborObject.CborMerkleLink(hashChild1),
                new CborObject.CborMerkleLink(hashChild2),
                new CborObject.CborLong(42))
        );
        MerkleNode root2Res = icfs.block.put(Collections.singletonList(root2.toByteArray()), Optional.of("cbor")).get(0);
        List<Multihash> update = icfs.pin.update(root1Res.hash, root2Res.hash, false);
    }

    @Test
    public void indirectPinTest() throws IOException {
        Multihash EMPTY = icfs.object._new(Optional.empty()).hash;
        icfs.api.MerkleNode data = icfs.object.patch(EMPTY, "set-data", Optional.of("childdata".getBytes()), Optional.empty(), Optional.empty());
        Multihash child = data.hash;

        icfs.api.MerkleNode tmp1 = icfs.object.patch(EMPTY, "set-data", Optional.of("parent1_data".getBytes()), Optional.empty(), Optional.empty());
        Multihash parent1 = icfs.object.patch(tmp1.hash, "add-link", Optional.empty(), Optional.of(child.toString()), Optional.of(child)).hash;
        icfs.pin.add(parent1);

        icfs.api.MerkleNode tmp2 = icfs.object.patch(EMPTY, "set-data", Optional.of("parent2_data".getBytes()), Optional.empty(), Optional.empty());
        Multihash parent2 = icfs.object.patch(tmp2.hash, "add-link", Optional.empty(), Optional.of(child.toString()), Optional.of(child)).hash;
        icfs.pin.add(parent2);
        icfs.pin.rm(parent1, true);

        Map<Multihash, Object> ls = icfs.pin.ls(ICFS.PinType.all);
        boolean childPresent = ls.containsKey(child);
        if (!childPresent)
            throw new IllegalStateException("Child not present!");

        icfs.repo.gc();
        Map<Multihash, Object> ls2 = icfs.pin.ls(ICFS.PinType.all);
        boolean childPresentAfterGC = ls2.containsKey(child);
        if (!childPresentAfterGC)
            throw new IllegalStateException("Child not present!");
}

    @Test
    public void objectPatch() throws IOException {
        MerkleNode obj = icfs.object._new(Optional.empty());
        Multihash base = obj.hash;
        // link tests
        String linkName = "alink";
        MerkleNode addLink = icfs.object.patch(base, "add-link", Optional.empty(), Optional.of(linkName), Optional.of(base));
        MerkleNode withLink = icfs.object.get(addLink.hash);
        if (withLink.links.size() != 1 || !withLink.links.get(0).hash.equals(base) || !withLink.links.get(0).name.get().equals(linkName))
            throw new RuntimeException("Added link not correct!");
        MerkleNode rmLink = icfs.object.patch(addLink.hash, "rm-link", Optional.empty(), Optional.of(linkName), Optional.empty());
        if (!rmLink.hash.equals(base))
            throw new RuntimeException("Adding not inverse of removing link!");

        // data tests
//            byte[] data = "some random textual data".getBytes();
        byte[] data = new byte[1024];
        new Random().nextBytes(data);
        MerkleNode patched = icfs.object.patch(base, "set-data", Optional.of(data), Optional.empty(), Optional.empty());
        byte[] patchedResult = icfs.object.data(patched.hash);
        if (!Arrays.equals(patchedResult, data))
            throw new RuntimeException("object.patch: returned data != stored data!");

        MerkleNode twicePatched = icfs.object.patch(patched.hash, "append-data", Optional.of(data), Optional.empty(), Optional.empty());
        byte[] twicePatchedResult = icfs.object.data(twicePatched.hash);
        byte[] twice = new byte[2*data.length];
        for (int i=0; i < 2; i++)
            System.arraycopy(data, 0, twice, i*data.length, data.length);
        if (!Arrays.equals(twicePatchedResult, twice))
            throw new RuntimeException("object.patch: returned data after append != stored data!");

    }

    // @Test
    // public void refsTest() throws IOException {
    //     List<Multihash> local = icfs.refs.local();
    //     for (Multihash ref: local) {
    //         Object refs = icfs.refs(ref, false);
    //     }
    // }

    @Test
    public void objectTest() throws IOException {
        MerkleNode _new = icfs.object._new(Optional.empty());
        Multihash pointer = Multihash.fromBase58("QmPZ9gcCEpqKTo6aq61g2nXGUhM4iCL3ewB6LDXZCtioEB");
        MerkleNode object = icfs.object.get(pointer);
        List<MerkleNode> newPointer = icfs.object.put(Arrays.asList(object.toJSONString().getBytes()));
        List<MerkleNode> newPointer2 = icfs.object.put("json", Arrays.asList(object.toJSONString().getBytes()));
        MerkleNode links = icfs.object.links(pointer);
        byte[] data = icfs.object.data(pointer);
        Map stat = icfs.object.stat(pointer);
    }

    @Test
    public void blockTest() throws IOException {
        MerkleNode pointer = new MerkleNode("QmPZ9gcCEpqKTo6aq61g2nXGUhM4iCL3ewB6LDXZCtioEB");
        Map stat = icfs.block.stat(pointer.hash);
        byte[] object = icfs.block.get(pointer.hash);
        List<MerkleNode> newPointer = icfs.block.put(Arrays.asList("Some random data...".getBytes()));
    }

    @Test
    public void bulkBlockTest() throws IOException {
        CborObject cbor = new CborObject.CborString("G'day ICFS!");
        byte[] raw = cbor.toByteArray();
        List<MerkleNode> bulkPut = icfs.block.put(Arrays.asList(raw, raw, raw, raw, raw), Optional.of("cbor"));
        List<Multihash> hashes = bulkPut.stream().map(m -> m.hash).collect(Collectors.toList());
        byte[] result = icfs.block.get(hashes.get(0));
        System.out.println();
    }

    @Ignore // Ignored because icfs frequently times out internally in the publish call
    @Test
    public void publish() throws Exception {
        // JSON document
        String json = "{\"name\":\"blogpost\",\"documents\":[]}";

        // Add a DAG node to ICFS
        MerkleNode merkleNode = icfs.dag.put("json", json.getBytes());
        Assert.assertEquals("expected to be bafy43jqbea2zebwb45peve22mdo6xgmk4czetvwpw3pijms6nzfxhkk726zw2" , "bafy43jqbea2zebwb45peve22mdo6xgmk4czetvwpw3pijms6nzfxhkk726zw2", merkleNode.hash.toString());

        // Get a DAG node
        byte[] res = icfs.dag.get((Cid) merkleNode.hash);
        Assert.assertEquals("Should be equals", JSONParser.parse(json), JSONParser.parse(new String(res)));

        // Publish to IPNS
        Map result = icfs.name.publish(merkleNode.hash, Optional.of("mytestkey"));

        // Resolve from IPNS
        // String resolved = icfs.name.resolve(Multihash.fromBase58((String) result.get("Name")));
        // Assert.assertEquals("Should be equals", resolved, "/icfs/" + merkleNode.hash.toString());
    }

    @Test
    public void pubsubSynchronous() throws Exception {
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
    }

    @Test
    public void pubsub() throws Exception {
        String topic = "topic" + System.nanoTime();
        Stream<Map<String, Object>> sub = icfs.pubsub.sub(topic);
        String data = "Hello!";
        Object pub = icfs.pubsub.pub(topic, data);
        Object pub2 = icfs.pubsub.pub(topic, "G'day");
        List<Map> results = sub.limit(2).collect(Collectors.toList());
        Assert.assertTrue( ! results.get(0).equals(Collections.emptyMap()));
    }

    private static String toEscapedHex(byte[] in) throws IOException {
        StringBuilder res = new StringBuilder();
        for (byte b : in) {
            res.append("\\x");
            res.append(String.format("%02x", b & 0xFF));
        }
        return res.toString();
    }

    /**
     *  Test that merkle links in values of a cbor map are followed during recursive pins
     */
    @Test
    public void merkleLinkInMap() throws IOException {
        Random r = new Random();
        CborObject.CborByteArray target = new CborObject.CborByteArray(("g'day ICFS!").getBytes());
        byte[] rawTarget = target.toByteArray();
        MerkleNode targetRes = icfs.block.put(Arrays.asList(rawTarget), Optional.of("cbor")).get(0);

        CborObject.CborMerkleLink link = new CborObject.CborMerkleLink(targetRes.hash);
        Map<String, CborObject> m = new TreeMap<>();
        m.put("alink", link);
        m.put("arr", new CborObject.CborList(Collections.emptyList()));
        CborObject.CborMap source = CborObject.CborMap.build(m);
        byte[] rawSource = source.toByteArray();
        MerkleNode sourceRes = icfs.block.put(Arrays.asList(rawSource), Optional.of("cbor")).get(0);

        CborObject.fromByteArray(rawSource);

        List<Multihash> add = icfs.pin.add(sourceRes.hash);
        icfs.repo.gc();
        icfs.repo.gc();

        List<Multihash> refs = icfs.refs(sourceRes.hash, true);
        Assert.assertTrue("refs returns links", refs.contains(targetRes.hash));

        byte[] bytes = icfs.block.get(targetRes.hash);
        Assert.assertTrue("same contents after GC", Arrays.equals(bytes, rawTarget));
        // These commands can be used to reproduce this on the command line
        String reproCommand1 = "printf \"" + toEscapedHex(rawTarget) + "\" | icfs block put --format=cbor";
        String reproCommand2 = "printf \"" + toEscapedHex(rawSource) + "\" | icfs block put --format=cbor";
        System.out.println();
    }

    @Test
    public void recursiveRefs() throws IOException {
        CborObject.CborByteArray leaf1 = new CborObject.CborByteArray(("G'day ICFS!").getBytes());
        byte[] rawLeaf1 = leaf1.toByteArray();
        MerkleNode leaf1Res = icfs.block.put(Arrays.asList(rawLeaf1), Optional.of("cbor")).get(0);

        CborObject.CborMerkleLink link = new CborObject.CborMerkleLink(leaf1Res.hash);
        Map<String, CborObject> m = new TreeMap<>();
        m.put("link1", link);
        CborObject.CborMap source = CborObject.CborMap.build(m);
        MerkleNode sourceRes = icfs.block.put(Arrays.asList(source.toByteArray()), Optional.of("cbor")).get(0);

        CborObject.CborByteArray leaf2 = new CborObject.CborByteArray(("G'day again, ICFS!").getBytes());
        byte[] rawLeaf2 = leaf2.toByteArray();
        MerkleNode leaf2Res = icfs.block.put(Arrays.asList(rawLeaf2), Optional.of("cbor")).get(0);

        Map<String, CborObject> m2 = new TreeMap<>();
        m2.put("link1", new CborObject.CborMerkleLink(sourceRes.hash));
        m2.put("link2", new CborObject.CborMerkleLink(leaf2Res.hash));
        CborObject.CborMap source2 = CborObject.CborMap.build(m2);
        MerkleNode rootRes = icfs.block.put(Arrays.asList(source2.toByteArray()), Optional.of("cbor")).get(0);

        List<Multihash> refs = icfs.refs(rootRes.hash, false);
        boolean correct = refs.contains(sourceRes.hash) && refs.contains(leaf2Res.hash) && refs.size() == 2;
        Assert.assertTrue("refs returns links", correct);

        List<Multihash> refsRecurse = icfs.refs(rootRes.hash, true);
        boolean correctRecurse = refs.contains(sourceRes.hash)
                && refs.contains(leaf1Res.hash)
                && refs.contains(leaf2Res.hash)
                && refs.size() == 3;
        Assert.assertTrue("refs returns links", correct);
    }

    /**
     *  Test that merkle links as a root object are followed during recursive pins
     */
    @Test
    public void rootMerkleLink() throws IOException {
        Random r = new Random();
        CborObject.CborByteArray target = new CborObject.CborByteArray(("g'day ICFS!" + r.nextInt()).getBytes());
        byte[] rawTarget = target.toByteArray();
        MerkleNode block1 = icfs.block.put(Arrays.asList(rawTarget), Optional.of("cbor")).get(0);
        Multihash block1Hash = block1.hash;
        byte[] retrievedObj1 = icfs.block.get(block1Hash);
        Assert.assertTrue("get inverse of put", Arrays.equals(retrievedObj1, rawTarget));

        CborObject.CborMerkleLink cbor2 = new CborObject.CborMerkleLink(block1.hash);
        byte[] obj2 = cbor2.toByteArray();
        MerkleNode block2 = icfs.block.put(Arrays.asList(obj2), Optional.of("cbor")).get(0);
        byte[] retrievedObj2 = icfs.block.get(block2.hash);
        Assert.assertTrue("get inverse of put", Arrays.equals(retrievedObj2, obj2));

        List<Multihash> add = icfs.pin.add(block2.hash);
        icfs.repo.gc();
        icfs.repo.gc();

        byte[] bytes = icfs.block.get(block1.hash);
        Assert.assertTrue("same contents after GC", Arrays.equals(bytes, rawTarget));
        // These commands can be used to reproduce this on the command line
        String reproCommand1 = "printf \"" + toEscapedHex(rawTarget) + "\" | icfs block put --format=cbor";
        String reproCommand2 = "printf \"" + toEscapedHex(obj2) + "\" | icfs block put --format=cbor";
        System.out.println();
    }

    /**
     *  Test that a cbor null is allowed as an object root
     */
    @Test
    public void rootNull() throws IOException {
        CborObject.CborNull cbor = new CborObject.CborNull();
        byte[] obj = cbor.toByteArray();
        MerkleNode block = icfs.block.put(Arrays.asList(obj), Optional.of("cbor")).get(0);
        byte[] retrievedObj = icfs.block.get(block.hash);
        Assert.assertTrue("get inverse of put", Arrays.equals(retrievedObj, obj));

        List<Multihash> add = icfs.pin.add(block.hash);
        icfs.repo.gc();
        icfs.repo.gc();

        // These commands can be used to reproduce this on the command line
        String reproCommand1 = "printf \"" + toEscapedHex(obj) + "\" | icfs block put --format=cbor";
        System.out.println();
    }

    /**
     *  Test that merkle links in a cbor list are followed during recursive pins
     */
    @Test
    public void merkleLinkInList() throws IOException {
        Random r = new Random();
        CborObject.CborByteArray target = new CborObject.CborByteArray(("g'day ICFS!" + r.nextInt()).getBytes());
        byte[] rawTarget = target.toByteArray();
        MerkleNode targetRes = icfs.block.put(Arrays.asList(rawTarget), Optional.of("cbor")).get(0);

        CborObject.CborMerkleLink link = new CborObject.CborMerkleLink(targetRes.hash);
        CborObject.CborList source = new CborObject.CborList(Arrays.asList(link));
        byte[] rawSource = source.toByteArray();
        MerkleNode sourceRes = icfs.block.put(Arrays.asList(rawSource), Optional.of("cbor")).get(0);

        List<Multihash> add = icfs.pin.add(sourceRes.hash);
        icfs.repo.gc();
        icfs.repo.gc();

        byte[] bytes = icfs.block.get(targetRes.hash);
        Assert.assertTrue("same contents after GC", Arrays.equals(bytes, rawTarget));
        // These commands can be used to reproduce this on the command line
        String reproCommand1 = "printf \"" + toEscapedHex(rawTarget) + "\" | icfs block put --format=cbor";
        String reproCommand2 = "printf \"" + toEscapedHex(rawSource) + "\" | icfs block put --format=cbor";
    }

    @Test
    public void fileContentsTest() throws IOException {
        icfs.repo.gc();
        List<Multihash> local = icfs.refs.local();
        for (Multihash hash: local) {
            try {
                Map ls = icfs.file.ls(hash);
                return;
            } catch (Exception e) {} // non unixfs files will throw an exception here
        }
    }

    @Test
    @Ignore("name test may hang forever")
    public void nameTest() throws IOException {
        MerkleNode pointer = new MerkleNode("QmPZ9gcCEpqKTo6aq61g2nXGUhM4iCL3ewB6LDXZCtioEB");
        Map pub = icfs.name.publish(pointer.hash);
        String name = "key" + System.nanoTime();
        Object gen = icfs.key.gen(name, Optional.of("rsa"), Optional.of("2048"));
        Map mykey = icfs.name.publish(pointer.hash, Optional.of(name));
        // String resolved = icfs.name.resolve(Multihash.fromBase58((String) pub.get("Name")));
    }

    // @Test
    // public void dnsTest() throws IOException {
    //     String domain = "icfs.bassze.com";
    //     String dns = icfs.dns(domain, true);
    // }

    public void mountTest() throws IOException {
        Map mount = icfs.mount(null, null);
    }

    @Test
    public void dhtTest() throws IOException {
        MerkleNode raw = icfs.block.put("Mathematics is wonderful".getBytes(), Optional.of("raw"));
//        Map get = icfs.dht.get(raw.hash);
//        Map put = icfs.dht.put("somekey", "somevalue");
        List<Map<String, Object>> findprovs = icfs.dht.findprovs(raw.hash);
        List<Peer> peers = icfs.swarm.peers();
        Map query = icfs.dht.query(peers.get(0).id);
        Map find = icfs.dht.findpeer(peers.get(0).id);
    }

    @Test
    public void localId() throws Exception {
        Map id = icfs.id();
        System.out.println();
    }

    @Test
    public void statsTest() throws IOException {
        Map stats = icfs.stats.bw();
    }

    public void resolveTest() throws IOException {
        Multihash hash = Multihash.fromBase58("QmatmE9msSfkKxoffpHwNLNKgwZG8eT9Bud6YoPab52vpy");
        Map res = icfs.resolve("ipns", hash, false);
    }

    @Test
    public void swarmTest() throws IOException {
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
    }

    @Test
    public void bootstrapTest() throws IOException {
        List<MultiAddress> bootstrap = icfs.bootstrap.list();
        System.out.println(bootstrap);
        // List<MultiAddress> rm = icfs.bootstrap.rm(bootstrap.get(0), false);
        // List<MultiAddress> add = icfs.bootstrap.add(bootstrap.get(0));
        System.out.println();
    }

    @Test
    public void diagTest() throws IOException {
        Map config = icfs.config.show();
        Object mdns = icfs.config.get("Discovery.MDNS.Interval");
        Object val = icfs.config.get("Datastore.GCPeriod");
        Map setResult = icfs.config.set("Datastore.GCPeriod", val);
        icfs.config.replace(new NamedStreamable.ByteArrayWrapper(JSONParser.toString(config).getBytes()));
//            Object log = icfs.log();
        String sys = icfs.diag.sys();
        String cmds = icfs.diag.cmds();
    }

    @Test
    public void toolsTest() throws IOException {
        String version = icfs.version();
        int major = Integer.parseInt(version.split("\\.")[0]);
        int minor = Integer.parseInt(version.split("\\.")[1]);
        assertTrue(major >= 0 && minor >= 3);     // Requires at least 0.4.0
        Map commands = icfs.commands();
    }

    @Test(expected = RuntimeException.class)
    public void testTimeoutFail() throws IOException {
        ICFS icfs = new ICFS(new MultiAddress("/dns4/icfs.baasze.com/tcp/5001")).timeout(10);
        icfs.cat(Multihash.fromBase58("bafk43jqbed4ac4h4f5xhmz5uvd44etmh43qw7tyu5wmozlpyhjmazmhuqhnnm"));
    }

    @Test
    public void testTimeoutOK() throws IOException {
        ICFS icfs = new ICFS(new MultiAddress("/dns4/icfs.baasze.com/tcp/5001")).timeout(1000);
        icfs.cat(Cid.decode("bafk43jqbed4ac4h4f5xhmz5uvd44etmh43qw7tyu5wmozlpyhjmazmhuqhnnm"));
    }

    // this api is disabled until deployment over ICFS is enabled
    public void updateTest() throws IOException {
        Object check = icfs.update.check();
        Object update = icfs.update();
    }

    private byte[] randomBytes(int len) {
        byte[] res = new byte[len];
        r.nextBytes(res);
        return res;
    }
}

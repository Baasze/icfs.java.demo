package icfs.demo.api;

import java.nio.file.*;
import java.util.*;

import org.junit.Assert;
import org.junit.Test;

import icfs.api.*;
import icfs.multiaddr.MultiAddress;
import icfs.utils.*;

public class RecursiveAddTest {

    private final ICFS icfs = new ICFS(new MultiAddress("/dns4/xxx.xxx.xxx/tcp/5001/http"));
    
    static Path TMPDATA = Paths.get("target/tmpdata");
    
    @Test
    public void testAdd() throws Exception {
        System.out.println("icfs version: " + icfs.version());

        String EXPECTED = "bafym3jqbead3q74g46oyxnq6anvu3jea7pvmkr2avyr4ndn727qx5zzrmg4ne";

        Path base = Files.createTempDirectory("test");
        Files.write(base.resolve("index.html"), "<html></html>".getBytes());
        Path js = base.resolve("js");
        js.toFile().mkdirs();
        Files.write(js.resolve("func.js"), "function() {console.log('Hey');}".getBytes());

        List<MerkleNode> add = icfs.add(new NamedStreamable.FileWrapper(base.toFile()));
        MerkleNode node = add.get(add.size() - 1);
        // System.out.println(node.hash.toString());
        Assert.assertEquals(EXPECTED, node.hash.toString());
    }

    @Test
    public void binaryRecursiveAdd() throws Exception {
        String EXPECTED = "bafym3jqbecrouucmvbu53g6yjphj3tzihj2lgl5ow6m5t3cgdefqghzvleyja";

        Path base = TMPDATA.resolve("bindata");
        base.toFile().mkdirs();
        byte[] bindata = new byte[1024*1024];
        new Random(28).nextBytes(bindata);
        Files.write(base.resolve("data.bin"), bindata);
        Path js = base.resolve("js");
        js.toFile().mkdirs();
        Files.write(js.resolve("func.js"), "function() {console.log('Hey');}".getBytes());

        List<MerkleNode> add = icfs.add(new NamedStreamable.FileWrapper(base.toFile()));
        MerkleNode node = add.get(add.size() - 1);
        Assert.assertEquals(EXPECTED, node.hash.toString());
    }

    @Test
    public void largeBinaryRecursiveAdd() throws Exception {
        String EXPECTED = "bafym3jqbec7rcr2p35c2fujuxmogpkrceul5m52av5xynyaefv2iwrsdpifpu";

        Path base = TMPDATA.resolve("largebindata");
        base.toFile().mkdirs();
        byte[] bindata = new byte[1 * 1024*1024];
        new Random(28).nextBytes(bindata);
        Files.write(base.resolve("data.bin"), bindata);
        new Random(496).nextBytes(bindata);
        Files.write(base.resolve("data2.bin"), bindata);
        Path js = base.resolve("js");
        js.toFile().mkdirs();
        Files.write(js.resolve("func.js"), "function() {console.log('Hey');}".getBytes());

        List<MerkleNode> add = icfs.add(new NamedStreamable.FileWrapper(base.toFile()));
        MerkleNode node = add.get(add.size() - 1);
        Assert.assertEquals(EXPECTED, node.hash.toString());
    }

    @Test
    public void largeBinaryInSubdirRecursiveAdd() throws Exception {
        String EXPECTED = "bafym3jqbecuct4wmxtcstwcdulohgsuo7wgvrpnjta6kpd4muiqkm5zqn4iik";

        Path base = TMPDATA.resolve("largebininsubdirdata");
        base.toFile().mkdirs();
        Path bindir = base.resolve("moredata");
        bindir.toFile().mkdirs();
        byte[] bindata = new byte[1 * 1024*1024];
        new Random(28).nextBytes(bindata);
        Files.write(bindir.resolve("data.bin"), bindata);
        new Random(496).nextBytes(bindata);
        Files.write(bindir.resolve("data2.bin"), bindata);

        Path js = base.resolve("js");
        js.toFile().mkdirs();
        Files.write(js.resolve("func.js"), "function() {console.log('Hey');}".getBytes());

        List<MerkleNode> add = icfs.add(new NamedStreamable.FileWrapper(base.toFile()));
        MerkleNode node = add.get(add.size() - 1);
        System.out.println(node.hash.toString());
        Assert.assertEquals(EXPECTED, node.hash.toString());
    }
}

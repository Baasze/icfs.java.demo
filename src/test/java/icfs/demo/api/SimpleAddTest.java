/*
 * @Description: 
 * @Author: kay
 * @Date: 2020-09-14 13:52:45
 * @LastEditTime: 2020-10-10 16:45:55
 * @LastEditors: kay
 */
package icfs.demo.api;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;
import icfs.api.*;
import icfs.utils.NamedStreamable.FileWrapper;
import icfs.multiaddr.MultiAddress;
import icfs.utils.*;

/**
 *
 * icfs daemon --enable-pubsub-experiment &
 *
 * icfs pin rm `icfs pin ls -qt recursive`
 *
 * icfs --api=/ip4/127.0.0.1/tcp/5001 add -r src/test/resources/html
 *
 */
public class SimpleAddTest {

    static final Map<String, String> cids = new LinkedHashMap<>();
    static {
        cids.put("index.html", "bafk43jqbeame7i3nlpztceh6abnje2mnshtmiofcjwwk2ekrc732y4ertzjuk");
        cids.put("html", "bafym3jqbecepn5lynmxzzv2m5ys2exdxj7g3zafo2lx4dsjsmkhmvwafnulgu");
    }

    ICFS icfs = new ICFS(new MultiAddress("/dns4/xxx.xxx.xxx/tcp/5001"));

    @Test
    public void testSingle() throws Exception {
        Path path = Paths.get("src/test/resources/html/index.html");
        NamedStreamable file = new FileWrapper(path.toFile());
        List<MerkleNode> tree = icfs.add(file);

        Assert.assertEquals(1, tree.size());
        Assert.assertEquals("index.html", tree.get(0).name.get());
        Assert.assertEquals(cids.get("index.html"), tree.get(0).hash.toString());
    }

    @Test
    public void testSingleWrapped() throws Exception {

        Path path = Paths.get("src/test/resources/html/index.html");
        NamedStreamable file = new FileWrapper(path.toFile());
        List<MerkleNode> tree = icfs.add(file, true);

        Assert.assertEquals(2, tree.size());
        Assert.assertEquals("index.html", tree.get(0).name.get());
        Assert.assertEquals(cids.get("index.html"), tree.get(0).hash.toString());
    }

    @Test
    public void testSingleOnlyHash() throws Exception {

        Path path = Paths.get("src/test/resources/html/index.html");
        NamedStreamable file = new FileWrapper(path.toFile());
        List<MerkleNode> tree = icfs.add(file, false, true);

        Assert.assertEquals(1, tree.size());
        Assert.assertEquals("index.html", tree.get(0).name.get());
        Assert.assertEquals(cids.get("index.html"), tree.get(0).hash.toString());
    }

    @Test
    public void testRecursive() throws Exception {

        Path path = Paths.get("src/test/resources/html");
        NamedStreamable file = new FileWrapper(path.toFile());
        List<MerkleNode> tree = icfs.add(file);

        Assert.assertEquals(8, tree.size());
        Assert.assertEquals("html", tree.get(7).name.get());
        Assert.assertEquals(cids.get("html"), tree.get(7).hash.toString());
    }

    @Test
    public void testRecursiveOnlyHash() throws Exception {

        Path path = Paths.get("src/test/resources/html");
        NamedStreamable file = new FileWrapper(path.toFile());
        List<MerkleNode> tree = icfs.add(file, false, true);

        Assert.assertEquals(8, tree.size());
        Assert.assertEquals("html", tree.get(7).name.get());
        Assert.assertEquals(cids.get("html"), tree.get(7).hash.toString());
    }
}

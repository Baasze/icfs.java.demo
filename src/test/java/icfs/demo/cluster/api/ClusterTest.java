/*
 * @Description: 
 * @Author: kay
 * @Date: 2020-09-04 17:42:15
 * @LastEditTime: 2020-10-10 16:46:04
 * @LastEditors: kay
 */
package icfs.demo.cluster.api;

import icfs.cluster.api.*;
import icfs.utils.*;
import java.io.*;
import org.junit.*;
import com.google.gson.Gson;
public class ClusterTest {

    @Test
    public void cluster(){
        ICFSCluster icfsCluster = new ICFSCluster("/dns4/xxx.xxx.xxx/tcp/9094/http");
        try {
            NamedStreamable.ByteArrayWrapper content = new NamedStreamable.ByteArrayWrapper("hello.txt",
                    "ICFS Cluster is awsome!".getBytes());
            String cid = icfsCluster.add(content).get(0).cid;
            String expected = "bafym3jqbeb7zlsez3vpc2axjptpijdghqarp7npko6fzq37xg7pmmzpg2plls";
            Assert.assertTrue("add content return Correct cid", cid.equals(expected));

            File f = new File(System.getProperty("user.dir") + "/src/test/java/icfs/demo/cluster/api/ClusterTest.java");
            InputStream in = new FileInputStream(f);
            NamedStreamable.InputStreamWrapper file = new NamedStreamable.InputStreamWrapper(in);
            System.out.println("icfsCluster add a file:" + new Gson().toJson(icfsCluster.add(file)) + "\n");

            File d = new File(System.getProperty("user.dir") + "/src/test/java/icfs/demo");
            NamedStreamable.FileWrapper dir = new NamedStreamable.FileWrapper(d);
            System.out.println("icfsCluster add a dir:" + new Gson().toJson(icfsCluster.add(dir)) + "\n");
            
            System.out.println("icfsCluster version:" + icfsCluster.version() + "\n");
            System.out.println("icfsCluster version:" + new Gson().toJson(icfsCluster.id()) + "\n");
            System.out.println("icfsCluster Pins :" + new Gson().toJson(icfsCluster.pins.add("bafk43jqbea6ns2jpkkrgcsvfabfnu5jea4u3rjix2uy2ovdgputbqexjastqw")) + "\n");
            System.out.println("icfsCluster Pins :" + new Gson().toJson(icfsCluster.pins.ls("bafk43jqbebtcrpsxhnl3hrk6o46vkfmguzqswi6ei53ubko4mfnpjvkibfp2e")) + "\n");
            System.out.println("icfsCluster Pins :" + new Gson().toJson(icfsCluster.pins.ls().get(0)) + "\n");
            System.out.println("icfsCluster Pins :" + new Gson().toJson(icfsCluster.pins.rm("bafk43jqbea6ns2jpkkrgcsvfabfnu5jea4u3rjix2uy2ovdgputbqexjastqw")) + "\n");
            System.out.println("icfsCluster Peers :" + new Gson().toJson(icfsCluster.peers.ls()));
            System.out.println("icfsCluster Recover :" + new Gson().toJson(icfsCluster.recover.recover("bafk43jqbea6ns2jpkkrgcsvfabfnu5jea4u3rjix2uy2ovdgputbqexjastqw")) + "\n");
            System.out.println("icfsCluster Recover :" + new Gson().toJson(icfsCluster.recover.recover().get(0)) + "\n");
            System.out.println("icfsCluster Allocation :" + new Gson().toJson(icfsCluster.allocations.ls().get(0)));
            System.out.println("icfsCluster Allocation :" + new Gson().toJson(icfsCluster.allocations.ls("bafk43jqbebtcrpsxhnl3hrk6o46vkfmguzqswi6ei53ubko4mfnpjvkibfp2e")) + "\n");
            System.out.println("icfsCluster Health :" + new Gson().toJson(icfsCluster.health.graph()) + "\n");
            System.out.println("icfsCluster Health :" + icfsCluster.health.metrics().get(0) + "\n");
            System.out.println("icfsCluster Health :" + new Gson().toJson(icfsCluster.health.metrics("ping").get(0)) + "\n");
            System.out.println("icfsCluster Health :" + new Gson().toJson(icfsCluster.health.metrics("freespace").get(0)) + "\n");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

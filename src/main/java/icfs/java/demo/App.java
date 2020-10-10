/*
 * @Description: 
 * @Author: kay
 * @Date: 2020-09-17 16:48:08
 * @LastEditTime: 2020-10-10 16:42:28
 * @LastEditors: kay
 */

package icfs.java.demo;

import icfs.api.*;          // icfs client
import icfs.cluster.api.*;  // cluster client
import icfs.utils.*; // NamedStreamable、JSONParser
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
      
      byte[] catResult = icfs.cat(Cid.decode(cid));           // icfs client cat file
      byte[] getResult = icfs.get(Cid.decode(cid)); // icfs client get file
      System.out.println(new String(catResult));
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Console;
import cn.hutool.core.util.CharsetUtil;
import cn.hutool.core.util.URLUtil;
import cn.hutool.core.util.XmlUtil;
import cn.hutool.crypto.digest.DigestAlgorithm;
import cn.hutool.crypto.digest.Digester;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import java.util.Arrays;
import java.util.HashMap;

public class GitalkAutoInit {

    /**
     * @param owner      	Gitalk 配置的 owner
     * @param repo      	Gitalk 配置的 repo
     * @param token			可从浏览器中获取
     * @param sitemapUrl
     * @param clientId      Gitalk 配置的 clientID
     * @param clientSecret  Gitalk 配置的 clientSecret
     * @param indexPageName
     */
    public static void initGitalk(String owner, String repo, String token, String sitemapUrl, String clientId, String clientSecret, String indexPageName) {
        Digester md5 = new Digester(DigestAlgorithm.MD5);
        String issueUrl = "https://api.github.com/repos/" + owner + "/" + repo + "/issues";

        String xmlStr = HttpUtil.get(sitemapUrl, CharsetUtil.CHARSET_UTF_8);
        // 解析 sitemap 文件
        Document doc = XmlUtil.parseXml(xmlStr);
        NodeList locs = XmlUtil.getNodeListByXPath("//urlset/url/loc", doc);

        Console.log("Gitalk 初始化开始");
        for (int i = 0; i < locs.getLength(); i++) {
            String loc = locs.item(i).getTextContent();
            String path = URLUtil.encode(URLUtil.getPath(loc));
            String label = md5.digestHex(path);
            HashMap<String, Object> paramMap = new HashMap<>();
            paramMap.put("client_id", clientId);
            paramMap.put("client_secret", clientSecret);
            paramMap.put("labels", "Gitalk," + label);
            HttpResponse issueListResp = HttpRequest.get(issueUrl).form(paramMap).execute();
            if (!issueListResp.isOk()) {
                Console.error("查询仓库评论失败，返回[{}]", issueListResp.body());
                return;
            }
            JSONArray issueList = JSONUtil.parseArray(HttpUtil.get(issueUrl, paramMap));

            // issueList 为空表示还未初始化
            if (CollUtil.isEmpty(issueList)) {
                HashMap<String, Object> params = new HashMap<>();
                params.put("body", loc);
                params.put("labels", Arrays.asList("Gitalk", label));
                String[] titles = path.split("/");
                String title;
                if (titles.length == 0) {
                    title = indexPageName;
                } else {
                    title = URLUtil.decode(titles[titles.length - 1], "utf-8");
                    // 获取 issue title
                    title = title.substring(3).replace(".html", "");
                }
                params.put("title", title);

                HttpResponse issueCreateResp = HttpRequest.post(issueUrl)
                    .header("Authorization", "token " + token)
                    .body(JSONUtil.toJsonStr(params))
                    .execute();
                if (issueCreateResp.isOk()) {
                    Console.log("[{}]初始化成功", path);
                } else {
                    Console.error("[{}]初始化失败，返回[{}]", path, issueCreateResp.body());
                    return;
                }
            } else {
                Console.log("[{}]无需再次初始化", path);
            }
        }
        Console.log("Gitalk 初始化结束");
    }

    public static void main(String[] args) {
        GitalkAutoInit.initGitalk("Sdky", "MyBook", "fe52ce1234543210b7a203991ffde258865ea5ed",
            "https://sdky.gitee.io/sitemap.xml", "b604e00b66666f971684", "85ee84c1a123d7994fccf2efd5ed951e1f41b676", "简介");
    }
}

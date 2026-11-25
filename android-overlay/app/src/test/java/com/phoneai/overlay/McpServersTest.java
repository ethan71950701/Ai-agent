package com.phoneai.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class McpServersTest {
    @Test public void convertMarksReadOnlyOnlyWhenServiceSaysSo() throws Exception {
        JSONArray in = new JSONArray()
                .put(new JSONObject().put("name", "search").put("description", "find").put("annotations", new JSONObject().put("readOnlyHint", true))
                        .put("inputSchema", new JSONObject().put("type", "object").put("$schema", "x")))
                .put(new JSONObject().put("name", "create_page").put("description", "make"))
                .put(new JSONObject().put("name", "delete").put("annotations", new JSONObject().put("readOnlyHint", false)));
        JSONArray out = McpServers.convert(in);
        assertEquals(3, out.length());
        assertTrue(out.getJSONObject(0).getBoolean("read_only"));
        assertFalse(out.getJSONObject(0).getJSONObject("input_schema").has("$schema"));
        assertFalse(out.getJSONObject(1).getBoolean("read_only"));   // 沒說：當作會寫入
        assertFalse(out.getJSONObject(2).getBoolean("read_only"));
        assertTrue(McpServers.describe("Notion", out).contains("create_page（會寫入）"));
    }

    @Test public void findByName() {
        List<McpServers.Server> l = new ArrayList<>();
        McpServers.Server a = new McpServers.Server(); a.id = "1"; a.name = "Notion"; l.add(a);
        McpServers.Server b = new McpServers.Server(); b.id = "2"; b.name = "Linear"; l.add(b);
        assertEquals("1", McpServers.find(l, "notion").id);
        assertEquals("2", McpServers.find(l, "Linear 任務").id);
        assertNull(McpServers.find(l, "spotify"));
        assertNull(McpServers.find(l, ""));
        McpServers.Server c = new McpServers.Server(); c.id = "3"; c.name = "Notion Work"; l.add(c);
        assertEquals("1", McpServers.find(l, "Notion").id);   // 完全相同優先
        assertNull(McpServers.find(l, "otion"));              // 兩個都包含：不猜
    }

    @Test public void urlCheck() {
        assertNull(McpServers.checkUrl("https://mcp.notion.com/mcp"));
        assertNull(McpServers.checkUrl("http://127.0.0.1:8080/mcp"));
        assertNotNull(McpServers.checkUrl("http://example.com/mcp"));
        assertNotNull(McpServers.checkUrl("mcp.notion.com"));
    }
}

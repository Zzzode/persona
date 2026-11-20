package hub

import (
	"testing"

	"github.com/nwverse/persona/server/internal/proto"
)

func TestCodexPushTitles(t *testing.T) {
	q := interactionPayload(proto.Interaction{Kind: proto.IxQuestion, Agent: "codex", Questions: []proto.Question{{Question: "Which color?"}}}, "server")
	alert := func(m map[string]any) map[string]any { return m["aps"].(map[string]any)["alert"].(map[string]any) }
	if alert(q)["title"] != "Codex 在问你" || alert(q)["body"] != "Which color?" {
		t.Errorf("%v", alert(q))
	}
	for _, agent := range []string{"traex", "traecli", "trae-cli"} {
		p := interactionPayload(proto.Interaction{Kind: proto.IxPermission, Agent: agent, Summary: "rm -rf build"}, "server")
		if alert(p)["title"] != "TRAE CLI 需要你授权" {
			t.Errorf("%s: %v", agent, alert(p))
		}
	}
}

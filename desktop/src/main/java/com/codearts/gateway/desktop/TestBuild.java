package com.codearts.gateway.desktop;
import org.json.JSONArray;
import com.codearts.gateway.core.QuestionReply;

public class TestBuild {
    public static void main(String[] args) {
        String q = "{\"question_id\":\"que_1a6b54108001dYpCaN7jfBCijf\",\"call_id\":\"call_da3007bb834f4fd18796935f\",\"questions\":[{\"question\":\"以下哪种编程语言最初由 Guido van Rossum 设计？\",\"header\":\"Python 基础题\",\"options\":[{\"label\":\"Python（推荐）\",\"description\":\"由 Guido van Rossum 于 1991 年发布\"},{\"label\":\"Java\",\"description\":\"由 James Gosling 等人设计\"}]}]}";
        String r = "User answered Claude's questions:\n  └ · 以下哪种编程语言最初由 Guido van Rossum 设计？ -> Python（推荐）";
        JSONArray a = QuestionReply.buildAnswers(q, r);
        System.out.println("BUILT_ANSWERS: " + a.toString());
    }
}
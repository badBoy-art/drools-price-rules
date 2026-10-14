package example.rules.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.JsonNode;

public class ExecuteRequest {
    @JsonAlias("business_code")
    public String businessCode;
    public JsonNode params;
}

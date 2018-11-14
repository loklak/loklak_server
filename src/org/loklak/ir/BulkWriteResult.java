package org.loklak.ir;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class BulkWriteResult {
    
    private Map<String, String> errors;
    private Set<String> created;
    
    public BulkWriteResult() {
        this.errors = new LinkedHashMap<>();
        this.created = new LinkedHashSet<>();
    }
    
    public Map<String, String> getErrors() {
        return this.errors;
    }
    
    public Set<String> getCreated() {
        return this.created;
    }
    
}

package il.ac.bgu.cs.bp.bpjs.context;

import il.ac.bgu.cs.bp.bpjs.execution.BProgramRunner;
import il.ac.bgu.cs.bp.bpjs.model.BProgram;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;

import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class ContextService implements Serializable {
    private static ContextService singleton;
    private final BProgram bp;
    private final BProgramRunner rnr;
    private final DfsContextualBProgramVerifier vfr;

    private AtomicInteger idCounter = new AtomicInteger(0); // remove b-thread name from state equality
    private Map<String, Function> queries = new HashMap<>();
    private Map<String, ContextEntity> CTX = new HashMap<>();
    private Map<String, List<ContextEntity>> active = new HashMap<>();
    private Set<ActiveChange> changes = new HashSet<>();
    private ArrayList<EffectFunction> effectFunctions = new ArrayList<>();
    private boolean transaction = false;

    public static ContextService GetInstance() {
        return singleton;
    }

    public static ContextService CreateInstance(BProgram bp, BProgramRunner rnr, DfsContextualBProgramVerifier vrf) {
        singleton = new ContextService(bp, rnr, vrf);
//        bp.putInGlobalScope("CTX", singleton); //TODO return
        return singleton;
    }

    private static class ContextServiceProxy implements Serializable {
        private final AtomicInteger idCounter;
        private final Map<String, ContextEntity> CTX;
        private final Map<String, List<ContextEntity>> active;

        ContextServiceProxy(ContextService cs) {
            this.idCounter = cs.idCounter;
            this.CTX = cs.CTX;
            this.active = cs.active;
        }

        private Object readResolve() throws ObjectStreamException {
            singleton.idCounter = this.idCounter;
            singleton.CTX = this.CTX;
            singleton.active = this.active;
            singleton.changes = new HashSet<>();

            return singleton;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ContextService contextService = (ContextService) o;
            return Objects.equals(CTX, contextService.CTX) &&
                Objects.equals(active, contextService.active);
        }

        @Override
        public int hashCode() {
            return Objects.hash(CTX, active);
        }
    }

    private Object writeReplace() throws ObjectStreamException {
        return new ContextServiceProxy(this);
    }

    public static int generateUniqueId() {
        return singleton.idCounter.incrementAndGet();
    }

    public void addEffectFunction(String eventName, Function function) {
        EffectFunction ef = new EffectFunction(function, eventName);
        this.effectFunctions.add(ef);
        if(rnr!=null)
            rnr.addListener(ef);
        else
            vfr.addListener(ef);
    }

    public List<ContextEntity> getActive(String queryName) {
        if(!active.containsKey(queryName))
            throw new IllegalArgumentException("No query with the name " + queryName);
        return active.get(queryName);
    }

    public List<ContextEntity> getQueryResults(String queryName) {
        if(!queries.containsKey(queryName))
            throw new IllegalArgumentException("No query with the name " + queryName);
        return getQueryResults(queries.get(queryName));
    }

    public List<ContextEntity> getQueryResults(Function query) {
        return CTX.values().stream().filter(entity -> runQuery(query, entity)).collect(Collectors.toList());
    }

    public ContextEntity getEntity(String id) {
        return CTX.get(id);
    }

    private boolean runQuery(String queryName, ContextEntity entity) {
        return runQuery(queries.get(queryName), entity);
    }

    private boolean runQuery(Function fct, ContextEntity entity) {
        Object result = fct.call(Context.getCurrentContext(), bp.getGlobalScope(), bp.getGlobalScope(), new Object[]{entity});
        return (boolean) Context.jsToJava(result,boolean.class);
    }

    public List<ActiveChange> recentChanges() {
        ArrayList<ActiveChange> res = new ArrayList<>(this.changes);
        this.changes = new HashSet<>();
        return res;
    }

    public void beginTransaction() {
        this.transaction = true;
    }

    public void endTransaction() {
        this.transaction = false;
        updateQueries();
    }

    private ContextService(BProgram bp, BProgramRunner rnr, DfsContextualBProgramVerifier vfr) {
        this.bp = bp;
        this.rnr = rnr;
        this.vfr = vfr;
    }

    public void insertEntity(ContextEntity entity) {
        if (CTX.containsKey(entity.id)) {
            throw new IllegalArgumentException("Key " + entity.id + " already exists");
        }
        ContextEntity attached = entity.attachedCopy(bp);
        CTX.put(attached.id, attached);
        if(!transaction) updateQueries();
    }

    public void updateEntity(ContextEntity detachedEntity) {
        if (!CTX.containsKey(detachedEntity.id)) {
            throw new IllegalArgumentException("Key " + detachedEntity.id + " does not exists");
        }
        CTX.get(detachedEntity.id).mergeChanges(bp, detachedEntity);
        if(!transaction) updateQueries();
    }

    public ActiveChange[] getRecentCtxEnd() {
        return changes.stream().filter(c -> c.type.equals("end")).toArray(ActiveChange[]::new);
    }

    public void deleteEntity(ContextEntity detachedEntity) {
        if (!CTX.containsKey(detachedEntity.id)) {
            throw new IllegalArgumentException("Key " + detachedEntity.id + " does not exists");
        }
        CTX.remove(detachedEntity.id);
        if(!transaction) updateQueries();
    }

    /*public boolean hasQuery(String q) {
        return active.containsKey(q);
    }*/

    public void registerQuery(String q, Function query) {
        if (active.containsKey(q)) {
            throw new IllegalArgumentException("Query " + q + " already exists");
        }
        active.put(q, new ArrayList<>());
        queries.put(q, query);
//        if(!transaction) updateQueries();
    }

    private void updateQueries() {
        active.entrySet().forEach(entry -> {
            List<ContextEntity> entryEntities = entry.getValue();
            // Remember the list of contexts that we already reported of
            List<ContextEntity> knownContexts = new LinkedList<>(entryEntities);
            // Update the list of contexts
            entryEntities.clear();
            entryEntities.addAll(getQueryResults(entry.getKey()));
            // Filter the contexts that we didn't yet report of
            List<ContextEntity> newContexts = new LinkedList<>(entryEntities);
            // noinspection SuspiciousMethodCalls
            newContexts.removeAll(knownContexts);
            // Compute the contexts that where just removed
            newContexts.stream().map(obj -> new ActiveChange(entry.getKey(), obj, "new")).forEach(changes::add);
            // noinspection SuspiciousMethodCalls
            knownContexts.removeAll(entryEntities);
            knownContexts.stream().map(obj -> new ActiveChange(entry.getKey(), obj, "end")).forEach(changes::add);
        });
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ContextService contextService = (ContextService) o;
        return Objects.equals(CTX, contextService.CTX) &&
            Objects.equals(active, contextService.active) &&
            Objects.equals(changes, contextService.changes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(CTX, active, changes);
    }

    public static class ActiveChange implements Serializable {
        public final String query;
        public final ContextEntity entity;
        public final String type;

        public ActiveChange(String query, ContextEntity entity, String type) {
            this.query = query;
            this.entity = entity;
            this.type = type;
        }

        @Override
        public String toString() {
            return "ActiveChange{" +
                "query=" + query +
                ", entity=" + entity +
                ", type='" + type + '\'' +
                '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ActiveChange that = (ActiveChange) o;
            return Objects.equals(query, that.query) &&
                Objects.equals(entity, that.entity) &&
                Objects.equals(type, that.type);
        }

        @Override
        public int hashCode() {
            return Objects.hash(query, entity, type);
        }
    }
}

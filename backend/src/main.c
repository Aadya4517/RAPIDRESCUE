/* main.c
   Emergency Dispatch - single file
   - Accepts your original dataset formats (nodes.csv and edges.csv)
   - Fuzzy location matching (substring, case-insensitive)
   - Clean console output
   Compile:
     gcc -std=c11 main.c -o dispatch_app
*/

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <ctype.h>

#ifdef _WIN32
#include <direct.h>
#define getcwd _getcwd
#endif

#define MAX_NODES 2000
#define INF 1e9
#define HEAP_MAX 1000

/* ---------------- Graph structures ---------------- */
typedef struct Edge {
    int dest;
    double weight;
    struct Edge *next;
} Edge;

typedef struct Node {
    int id;            /* internal index */
    long ext_id;       /* external id (from your CSV) */
    char name[128];
    char type[64];
    double lat, lon;
    Edge *adj;
} Node;

typedef struct Graph {
    int V;
    Node nodes[MAX_NODES];
} Graph;

/* ---------------- mapping ext_id -> internal index ---------------- */
typedef struct { long ext_id; int idx; } ExtMapEntry;
typedef struct { ExtMapEntry *entries; int count; int capacity; } ExtMap;

static void extmap_init(ExtMap *m) { m->entries = NULL; m->count = 0; m->capacity = 0; }
static void extmap_free(ExtMap *m) { free(m->entries); m->entries = NULL; m->count = m->capacity = 0; }
static void extmap_add(ExtMap *m, long ext_id, int idx) {
    if (m->count == m->capacity) {
        int newcap = (m->capacity == 0) ? 64 : m->capacity * 2;
        m->entries = realloc(m->entries, sizeof(ExtMapEntry) * newcap);
        m->capacity = newcap;
    }
    m->entries[m->count].ext_id = ext_id;
    m->entries[m->count].idx = idx;
    m->count++;
}
static int extmap_get(ExtMap *m, long ext_id) {
    for (int i = 0; i < m->count; ++i) if (m->entries[i].ext_id == ext_id) return m->entries[i].idx;
    return -1;
}

/* ---------------- Graph helpers ---------------- */
Graph *graph_create(void) {
    Graph *g = (Graph*)malloc(sizeof(Graph));
    if (!g) return NULL;
    g->V = 0;
    for (int i = 0; i < MAX_NODES; ++i) {
        g->nodes[i].id = i;
        g->nodes[i].ext_id = 0;
        g->nodes[i].name[0] = '\0';
        g->nodes[i].type[0] = '\0';
        g->nodes[i].lat = g->nodes[i].lon = 0.0;
        g->nodes[i].adj = NULL;
    }
    return g;
}

void add_edge(Graph *g, int src, int dest, double weight) {
    if (!g) return;
    if (src < 0 || src >= g->V || dest < 0 || dest >= g->V) return;
    Edge *e = (Edge*)malloc(sizeof(Edge));
    e->dest = dest; e->weight = weight; e->next = g->nodes[src].adj; g->nodes[src].adj = e;
}

int add_node(Graph *g, long ext_id, const char *name, const char *type, double lat, double lon) {
    if (!g) return -1;
    if (g->V >= MAX_NODES) return -1;
    int id = g->V++;
    g->nodes[id].id = id;
    g->nodes[id].ext_id = ext_id;
    strncpy(g->nodes[id].name, name, sizeof(g->nodes[id].name)-1);
    g->nodes[id].name[sizeof(g->nodes[id].name)-1] = '\0';
    strncpy(g->nodes[id].type, type, sizeof(g->nodes[id].type)-1);
    g->nodes[id].type[sizeof(g->nodes[id].type)-1] = '\0';
    g->nodes[id].lat = lat; g->nodes[id].lon = lon;
    g->nodes[id].adj = NULL;
    return id;
}

/* find internal index by exact name (case-insensitive) */
int find_node_by_name(Graph *g, const char *name) {
    if (!g || !name) return -1;
    for (int i = 0; i < g->V; ++i) if (strcasecmp(g->nodes[i].name, name) == 0) return i;
    return -1;
}

/* fuzzy substring match, case-insensitive */
int find_node_fuzzy(Graph *g, const char *input) {
    if (!g || !input) return -1;
    char low_input[256];
    snprintf(low_input, sizeof(low_input), "%s", input);
    for (int i = 0; low_input[i]; ++i) low_input[i] = tolower((unsigned char)low_input[i]);

    for (int i = 0; i < g->V; ++i) {
        char low_name[256];
        snprintf(low_name, sizeof(low_name), "%s", g->nodes[i].name);
        for (int j = 0; low_name[j]; ++j) low_name[j] = tolower((unsigned char)low_name[j]);
        if (strstr(low_name, low_input)) return i;
    }
    return -1;
}

void print_path(Graph *g, int parent[], int j) {
    if (!g) return;
    if (j == -1) return;
    print_path(g, parent, parent[j]);
    printf(" -> %s", g->nodes[j].name);
}

/* Dijkstra (O(V^2) simple implementation) */
void dijkstra(Graph *g, int src, double dist[], int parent[]) {
    if (!g) return;
    int n = g->V;
    int visited[MAX_NODES];
    for (int i = 0; i < n; ++i) { dist[i] = INF; parent[i] = -1; visited[i] = 0; }
    if (src < 0 || src >= n) return;
    dist[src] = 0.0;
    for (int count = 0; count < n - 1; ++count) {
        double min = INF; int u = -1;
        for (int v = 0; v < n; ++v) if (!visited[v] && dist[v] < min) { min = dist[v]; u = v; }
        if (u == -1) break;
        visited[u] = 1;
        for (Edge *e = g->nodes[u].adj; e; e = e->next) {
            int v = e->dest;
            if (!visited[v] && dist[u] + e->weight < dist[v]) {
                dist[v] = dist[u] + e->weight;
                parent[v] = u;
            }
        }
    }
}

/* ---------------- helpers: trim & BOM ---------------- */
static void trim(char *s) {
    if (!s) return;
    char *p = s;
    while (*p && isspace((unsigned char)*p)) p++;
    if (p != s) memmove(s, p, strlen(p) + 1);
    size_t len = strlen(s);
    while (len > 0 && isspace((unsigned char)s[len-1])) s[--len] = '\0';
}
static void strip_bom(char *s) {
    unsigned char *u = (unsigned char *)s;
    if (u[0]==0xEF && u[1]==0xBB && u[2]==0xBF) memmove(s, s+3, strlen(s+3)+1);
}
static int safe_parse_long(const char *tok, long *out) {
    if (!tok || !*tok) return 0;
    char *end = NULL; long v = strtol(tok, &end, 10); if (end == tok) return 0; *out = v; return 1;
}
static int safe_parse_double(const char *tok, double *out) {
    if (!tok || !*tok) return 0;
    char *end = NULL; double v = strtod(tok, &end); if (end == tok) return 0; *out = v; return 1;
}

/* ---------------- Load nodes.csv (your format) ----------------
   expected: ext_id,lat,lon,name,type
   tolerant: trims spaces, strips BOM, skips malformed lines silently
*/
int load_nodes_custom(Graph *g, const char *filename, ExtMap *emap) {
    FILE *f = fopen(filename, "r");
    if (!f) return -1;
    char line[1024]; int count = 0;
    while (fgets(line, sizeof(line), f)) {
        trim(line); if (line[0] == '\0') continue;
        strip_bom(line);
        /* parse with sscanf fallback */
        long extid=0; double lat=0.0, lon=0.0;
        char namebuf[128]={0}, typebuf[64]={0};
        if (sscanf(line, "%ld,%lf,%lf,%127[^,],%63[^\n]", &extid, &lat, &lon, namebuf, typebuf) == 5) {
            trim(namebuf); trim(typebuf);
            int idx = add_node(g, extid, namebuf, typebuf, lat, lon);
            if (idx >= 0) { extmap_add(emap, extid, idx); count++; }
        } else {
            /* skip malformed */
            continue;
        }
    }
    fclose(f);
    /* silent load: no per-line prints */
    return 0;
}

/* ---------------- Load edges.csv (your 6-column format) ----------------
   expected: edge_id,src_ext_id,dst_ext_id,distance_meters,travel_time,flag
   convert meters -> km as weight; create placeholder nodes if ext_id missing.
*/
int load_edges_custom(Graph *g, const char *filename, ExtMap *emap) {
    FILE *f = fopen(filename, "r");
    if (!f) return -1;
    char line[512]; int count = 0;
    while (fgets(line, sizeof(line), f)) {
        trim(line); if (line[0] == '\0') continue;
        strip_bom(line);
        /* tokenize using strtok (portable) */
        char *tok[6] = {0}; int tokc = 0;
        char buf[512]; strncpy(buf, line, sizeof(buf)-1); buf[sizeof(buf)-1] = '\0';
        char *t = strtok(buf, ",");
        while (t && tokc < 6) { trim(t); tok[tokc++] = t; t = strtok(NULL, ","); }
        if (tokc < 3) continue; /* not enough tokens */
        long src_ext = 0, dst_ext = 0; if (!safe_parse_long(tok[1], &src_ext)) continue;
        if (!safe_parse_long(tok[2], &dst_ext)) continue;
        double dist_m = 0.0; if (tokc >= 4) safe_parse_double(tok[3], &dist_m);
        double weight_km = (dist_m > 0.0) ? (dist_m / 1000.0) : 1.0;
        int src_idx = extmap_get(emap, src_ext);
        if (src_idx == -1) {
            char namebuf[64]; snprintf(namebuf, sizeof(namebuf), "node_%ld", src_ext);
            src_idx = add_node(g, src_ext, namebuf, "unknown", 0.0, 0.0);
            extmap_add(emap, src_ext, src_idx);
        }
        int dst_idx = extmap_get(emap, dst_ext);
        if (dst_idx == -1) {
            char namebuf[64]; snprintf(namebuf, sizeof(namebuf), "node_%ld", dst_ext);
            dst_idx = add_node(g, dst_ext, namebuf, "unknown", 0.0, 0.0);
            extmap_add(emap, dst_ext, dst_idx);
        }
        add_edge(g, src_idx, dst_idx, weight_km);
        add_edge(g, dst_idx, src_idx, weight_km);
        count++;
    }
    fclose(f);
    return 0;
}

/* ---------------- Priority queue (calls) ---------------- */
struct Call { int id; char loc[200]; int sev; int time; };
static struct Call heapQ[HEAP_MAX];
static int heap_size = 0;
static int compare_calls(const struct Call *a, const struct Call *b) {
    if (a->sev != b->sev) return a->sev > b->sev;
    return a->time < b->time;
}
static void swap_calls(struct Call *a, struct Call *b) { struct Call t = *a; *a = *b; *b = t; }

void insert_call(struct Call c) {
    if (heap_size + 1 >= HEAP_MAX) return;
    heapQ[++heap_size] = c;
    int i = heap_size;
    while (i > 1 && compare_calls(&heapQ[i], &heapQ[i/2])) { swap_calls(&heapQ[i], &heapQ[i/2]); i /= 2; }
}
struct Call extract_call(void) {
    struct Call nullC = {0, "", 0, 0};
    if (heap_size == 0) return nullC;
    struct Call root = heapQ[1];
    heapQ[1] = heapQ[heap_size--];
    int i = 1;
    while (1) {
        int largest = i, l = 2*i, r = 2*i+1;
        if (l <= heap_size && compare_calls(&heapQ[l], &heapQ[largest])) largest = l;
        if (r <= heap_size && compare_calls(&heapQ[r], &heapQ[largest])) largest = r;
        if (largest == i) break;
        swap_calls(&heapQ[i], &heapQ[largest]);
        i = largest;
    }
    return root;
}
int is_queue_empty(void) { return heap_size == 0; }

/* ---------------- Units ---------------- */
typedef struct { int id; int node_idx; char type[32]; int available; } Unit;
Unit units[200]; int unit_count = 0;

void add_unit(int node_idx, const char *type, int id) {
    if (unit_count >= (int)(sizeof(units)/sizeof(units[0]))) return;
    units[unit_count].node_idx = node_idx;
    strncpy(units[unit_count].type, type, sizeof(units[unit_count].type)-1);
    units[unit_count].id = id;
    units[unit_count].available = 1;
    unit_count++;
}

void init_units_from_graph(Graph *g) {
    unit_count = 0;
    for (int i = 0; i < g->V; ++i) {
        if (node_matches_type_or_name(g, i, "hospital")) add_unit(i, "ambulance", 1000 + i);
        if (node_matches_type_or_name(g, i, "fire")) add_unit(i, "fire", 2000 + i);
        if (node_matches_type_or_name(g, i, "police")) add_unit(i, "police", 3000 + i);
    }
}

/* helper used in init_units */
int node_matches_type_or_name(Graph *g, int idx, const char *key) {
    if (!g || !key || idx < 0 || idx >= g->V) return 0;
    char lname[256], ltype[256], lkey[256];
    snprintf(lname, sizeof(lname), "%s", g->nodes[idx].name);
    snprintf(ltype, sizeof(ltype), "%s", g->nodes[idx].type);
    snprintf(lkey, sizeof(lkey), "%s", key);
    for (int i = 0; lname[i]; ++i) lname[i] = tolower((unsigned char)lname[i]);
    for (int i = 0; ltype[i]; ++i) ltype[i] = tolower((unsigned char)ltype[i]);
    for (int i = 0; lkey[i]; ++i) lkey[i] = tolower((unsigned char)lkey[i]);
    return (strstr(lname, lkey) != NULL) || (strstr(ltype, lkey) != NULL);
}

/* ---------------- Dispatch (automatic) ---------------- */
void dispatch_all(Graph *g) {
    while (!is_queue_empty()) {
        struct Call inc = extract_call();
        int target = find_node_fuzzy(g, inc.loc);
        if (target == -1) {
            printf("Location '%s' not found. Skipping.\n", inc.loc);
            continue;
        }
        char required_type[32];
        if (inc.sev >= 4) strcpy(required_type, "ambulance");
        else if (inc.sev == 3) strcpy(required_type, "police");
        else strcpy(required_type, "fire");

        double best = INF; int bestUnit = -1;
        double distarr[MAX_NODES]; int parent[MAX_NODES];

        for (int i = 0; i < unit_count; ++i) {
            if (!units[i].available) continue;
            if (strcasecmp(units[i].type, required_type) != 0) continue;
            dijkstra(g, units[i].node_idx, distarr, parent);
            if (distarr[target] < best) { best = distarr[target]; bestUnit = i; }
        }
        if (bestUnit == -1) {
            printf("All %s units busy. Skipping '%s'.\n", required_type, inc.loc);
            continue;
        }
        units[bestUnit].available = 0;
        double avg_speed = 40.0;
        double eta_min = (best / avg_speed) * 60.0;
        printf("\nDispatching %s unit %d to '%s'\n", units[bestUnit].type, units[bestUnit].id, g->nodes[target].name);
        printf(" Distance: %.2f km | ETA: %.1f min\n", best, eta_min);
        /* recompute parent for printing path */
        double tmpd[MAX_NODES]; int parent2[MAX_NODES];
        dijkstra(g, units[bestUnit].node_idx, tmpd, parent2);
        printf(" Route:"); print_path(g, parent2, target); printf("\n");
        units[bestUnit].available = 1; /* immediate free (simulate) */
        printf(" Unit %d now available.\n", units[bestUnit].id);
    }
    printf("\nAll incidents processed.\n");
}

/* ---------------- Main ---------------- */
int main(void) {
    Graph *g = graph_create();
    if (!g) { fprintf(stderr, "Memory error\n"); return 1; }

    ExtMap emap; extmap_init(&emap);
    if (load_nodes_custom(g, "nodes.csv", &emap) == -1) {
        fprintf(stderr, "Failed to open nodes.csv\n"); return 1;
    }
    if (load_edges_custom(g, "edges.csv", &emap) == -1) {
        fprintf(stderr, "Failed to open edges.csv\n"); return 1;
    }

    init_units_from_graph(g);
    printf("System ready with %d locations and %d units.\n", g->V, unit_count);
    printf("Severity guide: 4-5 => Hospital/Ambulance | 3 => Police | 1-2 => Fire\n\n");

    char cont = 'y'; int call_id = 1; int timestamp = 1;
    while (tolower((unsigned char)cont) == 'y') {
        struct Call c; c.id = call_id++;
        printf("Enter location: ");
        if (!fgets(c.loc, sizeof(c.loc), stdin)) break;
        c.loc[strcspn(c.loc, "\n")] = '\0';
        if (!c.loc[0]) { printf("Empty input — try again.\n"); continue; }
        printf("Enter severity (1-5): ");
        if (scanf("%d", &c.sev) != 1) { printf("Invalid severity\n"); return 1; }
        c.time = timestamp++;
        int ch = getchar(); (void)ch;
        insert_call(c);
        printf("Recorded: [%s] (severity %d)\n", c.loc, c.sev);
        printf("Add another? (y/n): ");
        if (scanf(" %c", &cont) != 1) break;
        ch = getchar(); (void)ch;
    }

    printf("\nAutomatic dispatch starting...\n");
    dispatch_all(g);

    /* cleanup */
    extmap_free(&emap);
    for (int i = 0; i < g->V; ++i) {
        Edge *e = g->nodes[i].adj;
        while (e) { Edge *tmp = e; e = e->next; free(tmp); }
    }
    free(g);
    return 0;
}

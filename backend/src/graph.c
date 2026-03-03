

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <ctype.h>

#define LINEBUF 4096
#define INITIAL_NODES 1024
#define INITIAL_EDGES 16384
#define INF 1e18


typedef struct { long long key; int val; char used; } LLMapEntry;
typedef struct { LLMapEntry *table; int cap; int size; } LLMap;
static unsigned long long hash_u64(unsigned long long x){
    x = (x ^ (x >> 30)) * 0xbf58476d1ce4e5b9ULL;
    x = (x ^ (x >> 27)) * 0x94d049bb133111ebULL;
    x = x ^ (x >> 31);
    return x;
}
LLMap* llmap_create(int cap){
    LLMap *m = malloc(sizeof(LLMap));
    m->cap = 1; while (m->cap < cap) m->cap <<= 1;
    m->table = calloc(m->cap, sizeof(LLMapEntry)); m->size = 0; return m;
}
void llmap_free(LLMap *m){ if(!m) return; free(m->table); free(m); }
int llmap_find(LLMap *m, long long key){
    unsigned long long h = hash_u64((unsigned long long)key);
    int idx = (int)(h & (m->cap - 1));
    while (m->table[idx].used){
        if (m->table[idx].key == key) return m->table[idx].val;
        idx = (idx + 1) & (m->cap - 1);
    }
    return -1;
}
void llmap_put(LLMap *m, long long key, int val){
    if (m->size * 2 >= m->cap){
        int newcap = m->cap * 2;
        LLMapEntry *old = m->table; int oldcap = m->cap;
        m->table = calloc(newcap, sizeof(LLMapEntry)); m->cap = newcap; m->size = 0;
        for (int i=0;i<oldcap;i++) if (old[i].used){
            unsigned long long h = hash_u64((unsigned long long)old[i].key);
            int idx = (int)(h & (m->cap - 1));
            while (m->table[idx].used) idx = (idx + 1) & (m->cap - 1);
            m->table[idx].used = 1; m->table[idx].key = old[i].key; m->table[idx].val = old[i].val; m->size++;
        }
        free(old);
    }
    unsigned long long h = hash_u64((unsigned long long)key);
    int idx = (int)(h & (m->cap - 1));
    while (m->table[idx].used){
        if (m->table[idx].key == key){ m->table[idx].val = val; return; }
        idx = (idx + 1) & (m->cap - 1);
    }
    m->table[idx].used = 1; m->table[idx].key = key; m->table[idx].val = val; m->size++;
}


typedef struct { int to; double weight; int next; } Edge;
typedef struct {
    int V;
    int edge_count;
    int edge_cap;
    Edge *edges;
    int *head;
    long long *ext_id;
    double *lat, *lon;
    char **name, **type;
    LLMap *idmap;
} Graph;

static int global_node_cap = INITIAL_NODES;
Graph* graph_create(int node_cap){
    Graph *g = malloc(sizeof(Graph));
    g->V = 0; g->edge_count = 0; g->edge_cap = INITIAL_EDGES;
    g->edges = malloc(sizeof(Edge) * g->edge_cap);
    g->head = malloc(sizeof(int) * node_cap);
    g->ext_id = malloc(sizeof(long long) * node_cap);
    g->lat = malloc(sizeof(double) * node_cap); g->lon = malloc(sizeof(double) * node_cap);
    g->name = malloc(sizeof(char*) * node_cap); g->type = malloc(sizeof(char*) * node_cap);
    for (int i=0;i<node_cap;i++){ g->head[i] = -1; g->ext_id[i]=0; g->lat[i]=g->lon[i]=0.0; g->name[i]=NULL; g->type[i]=NULL; }
    g->idmap = llmap_create(node_cap*2 + 16);
    global_node_cap = node_cap; return g;
}
void graph_ensure_nodecap(Graph *g, int need){
    if (need <= global_node_cap) return;
    int cap = global_node_cap; while (need > cap) cap *= 2;
    g->head = realloc(g->head, sizeof(int) * cap);
    g->ext_id = realloc(g->ext_id, sizeof(long long) * cap);
    g->lat = realloc(g->lat, sizeof(double) * cap); g->lon = realloc(g->lon, sizeof(double) * cap);
    g->name = realloc(g->name, sizeof(char*) * cap); g->type = realloc(g->type, sizeof(char*) * cap);
    for (int i=global_node_cap;i<cap;i++){ g->head[i] = -1; g->ext_id[i]=0; g->lat[i]=g->lon[i]=0.0; g->name[i]=NULL; g->type[i]=NULL; }
    global_node_cap = cap;
}
int graph_add_node(Graph *g, long long ext, double lat, double lon, const char *name, const char *type){
    int idx = g->V++; graph_ensure_nodecap(g, g->V);
    g->ext_id[idx] = ext; g->lat[idx] = lat; g->lon[idx] = lon;
    g->name[idx] = (name && strlen(name)) ? strdup(name) : NULL;
    g->type[idx] = (type && strlen(type)) ? strdup(type) : NULL;
    g->head[idx] = -1; llmap_put(g->idmap, ext, idx); return idx;
}
void graph_add_edge(Graph *g, int u, int v, double w){
    if (g->edge_count >= g->edge_cap){ g->edge_cap *= 2; g->edges = realloc(g->edges, sizeof(Edge) * g->edge_cap); }
    int ei = g->edge_count++; g->edges[ei].to = v; g->edges[ei].weight = w; g->edges[ei].next = g->head[u]; g->head[u] = ei;
}
int graph_get_or_create(Graph *g, long long ext){
    int idx = llmap_find(g->idmap, ext); if (idx != -1) return idx;
    return graph_add_node(g, ext, 0.0, 0.0, NULL, NULL);
}
void graph_free(Graph *g){
    if (!g) return;
    for (int i=0;i<g->V;i++){ if (g->name[i]) free(g->name[i]); if (g->type[i]) free(g->type[i]); }
    free(g->head); free(g->ext_id); free(g->lat); free(g->lon); free(g->name); free(g->type);
    free(g->edges); llmap_free(g->idmap); free(g);
}


static void trim(char *s){ char *p=s; while(*p && (*p==' '||*p=='\t')) p++; if (p!=s) memmove(s,p,strlen(p)+1); int len=strlen(s); while(len>0 && (s[len-1]=='\r'||s[len-1]=='\n'||s[len-1]==' '||s[len-1]=='\t')) s[--len]=0; }
static void str_to_lower(const char *src, char *dst){ while (*src){ *dst = (char)tolower((unsigned char)*src); src++; dst++; } *dst = 0; }


int node_matches_type_or_name(Graph *g, int idx, const char *requested){
    if (!requested) return 0;
    char rl[256]; str_to_lower(requested, rl);
    if (g->type[idx]){
        char t[256]; str_to_lower(g->type[idx], t);
        if (strstr(t, rl)) return 1;
    }
    if (g->name[idx]){
        char n[1024]; str_to_lower(g->name[idx], n);
        if (strstr(n, rl)) return 1;
    }
    return 0;
}

int name_match_ci(const char *node_name, const char *query){ if (!node_name || !query) return 0; char a[1024], b[1024]; str_to_lower(node_name,a); str_to_lower(query,b); return strstr(a,b) != NULL; }
int type_is_allowed(const char *type){ if (!type) return 0; char t[256]; str_to_lower(type,t); if (strstr(t,"hospital")!=NULL) return 1; if (strstr(t,"fire")!=NULL) return 1; if (strstr(t,"police")!=NULL) return 1; return 0; }
int type_matches_requested(const char *type, const char *requested){ if (!type || !requested) return 0; char t[256], r[256]; str_to_lower(type,t); str_to_lower(requested,r); return strstr(t,r) != NULL; }


int load_nodes(Graph *g, const char *fname){
    FILE *f = fopen(fname,"r"); if (!f){ perror("open nodes.csv"); return -1; }
    char line[LINEBUF];
    if (!fgets(line, LINEBUF, f)){ fclose(f); return 0; } // header
    while (fgets(line, LINEBUF, f)){
        trim(line); if (strlen(line)==0) continue;
        char *tok = strtok(line, ","); if (!tok) continue;
        long long ext = atoll(tok); if (ext==0 && tok[0] != '0') continue;
        tok = strtok(NULL, ","); double lat = tok ? atof(tok) : 0.0;
        tok = strtok(NULL, ","); double lon = tok ? atof(tok) : 0.0;
        tok = strtok(NULL, ","); char namebuf[1024] = ""; if (tok) { strncpy(namebuf, tok, 1023); namebuf[1023]=0; trim(namebuf); }
        tok = strtok(NULL, ","); char typebuf[256] = ""; if (tok) { strncpy(typebuf, tok, 255); typebuf[255]=0; trim(typebuf); }
        int idx = llmap_find(g->idmap, ext);
        if (idx == -1) graph_add_node(g, ext, lat, lon, namebuf, typebuf);
        else {
            g->lat[idx]=lat; g->lon[idx]=lon;
            if (g->name[idx]) free(g->name[idx]); if (strlen(namebuf)) g->name[idx]=strdup(namebuf); else g->name[idx]=NULL;
            if (g->type[idx]) free(g->type[idx]); if (strlen(typebuf)) g->type[idx]=strdup(typebuf); else g->type[idx]=NULL;
        }
    }
    fclose(f); return g->V;
}
int load_edges(Graph *g, const char *fname){
    FILE *f = fopen(fname,"r"); if (!f){ perror("open edges.csv"); return -1; }
    char line[LINEBUF];
    if (!fgets(line, LINEBUF, f)){ fclose(f); return 0; } // header
    int count=0;
    while (fgets(line, LINEBUF, f)){
        trim(line); if (strlen(line)==0) continue;
        char *tok = strtok(line, ","); if (!tok) continue; // edge_id
        tok = strtok(NULL, ","); if (!tok) continue; long long from = atoll(tok);
        tok = strtok(NULL, ","); if (!tok) continue; long long to = atoll(tok);
        tok = strtok(NULL, ","); tok = strtok(NULL, ","); double travel_time = tok ? atof(tok) : 0.0;
        tok = strtok(NULL, ","); int one_way = tok ? atoi(tok) : 0;
        int u = graph_get_or_create(g, from);
        int v = graph_get_or_create(g, to);
        if (one_way) graph_add_edge(g, u, v, travel_time);
        else { graph_add_edge(g, u, v, travel_time); graph_add_edge(g, v, u, travel_time); }
        count++;
    }
    fclose(f); return count;
}


typedef struct { int node; double dist; } HNode;
typedef struct { HNode *a; int size; int cap; } MinHeap;
MinHeap* heap_create(int cap){ MinHeap *h = malloc(sizeof(MinHeap)); h->cap = cap>16?cap:16; h->a = malloc(sizeof(HNode)*(h->cap+1)); h->size=0; return h;}
void heap_free(MinHeap *h){ if(!h) return; free(h->a); free(h); } void heap_swap(HNode *x, HNode *y){ HNode t=*x; *x=*y; *y=t; }
void heap_push(MinHeap *h, int node, double dist){ if (h->size+1>h->cap){ h->cap*=2; h->a=realloc(h->a,sizeof(HNode)*(h->cap+1));} int i=++h->size; h->a[i].node=node; h->a[i].dist=dist; while(i>1){ int p=i>>1; if (h->a[p].dist<=h->a[i].dist) break; heap_swap(&h->a[p],&h->a[i]); i=p; } }
int heap_empty(MinHeap *h){ return h->size==0; } HNode heap_pop(MinHeap *h){ HNode ret = h->a[1]; h->a[1]=h->a[h->size--]; int i=1; while(1){ int l=i<<1, r=l+1, s=i; if (l<=h->size && h->a[l].dist < h->a[s].dist) s=l; if (r<=h->size && h->a[r].dist < h->a[s].dist) s=r; if (s==i) break; heap_swap(&h->a[i], &h->a[s]); i=s; } return ret; }

void dijkstra(Graph *g, int src, double *dist, int *parent){
    int n = g->V; for (int i=0;i<n;i++){ dist[i]=INF; parent[i]=-1; } dist[src]=0.0;
    MinHeap *pq = heap_create(n>16?n:16); heap_push(pq, src, 0.0); char *vis = calloc(n,1);
    while (!heap_empty(pq)){
        HNode hn = heap_pop(pq); int u = hn.node; double d = hn.dist;
        if (d > dist[u]) continue; if (vis[u]) continue; vis[u]=1;
        for (int e = g->head[u]; e!=-1; e = g->edges[e].next){
            int v = g->edges[e].to; double w = g->edges[e].weight;
            double nd = dist[u] + w;
            if (nd < dist[v]){ dist[v] = nd; parent[v]=u; heap_push(pq, v, nd); }
        }
    }
    free(vis); heap_free(pq);
}


void print_path(Graph *g, int *parent, int dest){
    if (dest < 0 || dest >= g->V) { printf("Invalid dest\n"); return; }
    int *stack = malloc(sizeof(int) * g->V); int top = 0, cur = dest;
    while (cur != -1){ stack[top++] = cur; cur = parent[cur]; }
    for (int i = top-1; i>=0; i--){
        int idx = stack[i];
        if (g->name[idx]) printf("%s", g->name[idx]); else printf("%lld", g->ext_id[idx]);
        if (i) printf(" -> ");
    }
    printf("\n"); free(stack);
}


int find_node_by_name(Graph *g, const char *query){
    char qlow[1024]; str_to_lower(query, qlow);
    for (int i=0;i<g->V;i++){
        if (g->name[i]){
            char lnm[1024]; str_to_lower(g->name[i], lnm);
            if (strstr(lnm, qlow)) return i;
        }
    }
    return -1;
}


int find_nearest_of_type_from(Graph *g, int start_idx, const char *requested_type){
    if (!g || start_idx < 0 || start_idx >= g->V) return -1;
    double *dist = malloc(sizeof(double) * g->V);
    int *parent = malloc(sizeof(int) * g->V);
    if (!dist || !parent) { free(dist); free(parent); return -1; }
    dijkstra(g, start_idx, dist, parent);
    double best = INF; int best_idx = -1;
    for (int i=0;i<g->V;i++){
        if (node_matches_type_or_name(g, i, requested_type)){
            if (dist[i] < best){ best = dist[i]; best_idx = i; }
        }
    }
    free(dist); free(parent);
    return best_idx;
}


int parse_any_keyword_strict(const char *s, char *out_type){ char low[256]; str_to_lower(s, low);
    if (strcmp(low, "hospital")==0 || strcmp(low, "any hospital")==0 || strcmp(low, "from hospital")==0) { strcpy(out_type,"hospital"); return 1; }
    if (strcmp(low, "fire")==0 || strcmp(low, "firestation")==0 || strcmp(low,"fire station")==0 || strcmp(low,"any fire")==0 || strcmp(low,"any fire station")==0 || strcmp(low,"from fire")==0) { strcpy(out_type,"fire"); return 1; }
    if (strcmp(low, "police")==0 || strcmp(low,"police station")==0 || strcmp(low,"any police")==0 || strcmp(low,"from police")==0) { strcpy(out_type,"police"); return 1; }
    if (strncmp(low, "any ", 4) == 0){
        if (strstr(low, "hospital")){ strcpy(out_type,"hospital"); return 1; }
        if (strstr(low, "fire")){ strcpy(out_type,"fire"); return 1; }
        if (strstr(low, "police")){ strcpy(out_type,"police"); return 1; }
    }
    if (strncmp(low, "from any ", 9) == 0){
        if (strstr(low, "hospital")){ strcpy(out_type,"hospital"); return 1; }
        if (strstr(low, "fire")){ strcpy(out_type,"fire"); return 1; }
        if (strstr(low, "police")){ strcpy(out_type,"police"); return 1; }
    }
    return 0;
}


int main(int argc, char **argv){
   if (argc < 3){ 
    printf("Usage: %s nodes.csv edges.csv\n", argv[0]); 
    return 1; 
}

Graph *g = graph_create(4096);

int n_nodes = load_nodes(g, argv[1]); 
if (n_nodes < 0) return 1;

int n_edges = load_edges(g, argv[2]); 
if (n_edges < 0) return 1;


    char srcq[512], dstq[512];
    printf("Enter source place name :\n> ");
    getchar(); 
    if (!fgets(srcq, sizeof(srcq), stdin)){ printf("Input error\n"); graph_free(g); return 1; }
    trim(srcq); if (strlen(srcq)==0){ printf("Empty input\n"); graph_free(g); return 1; }

    printf("Enter destination place name (or 'hospital'/'fire'/'police'):\n> ");
    if (!fgets(dstq, sizeof(dstq), stdin)){ printf("Input error\n"); graph_free(g); return 1; }
    trim(dstq); if (strlen(dstq)==0){ printf("Empty input\n"); graph_free(g); return 1; }

    // determine if source is generic
    char src_req_type[64]; int src_is_any = parse_any_keyword_strict(srcq, src_req_type);

    int src_idx = -1;
    if (src_is_any){
        
    } else {
        
        int f = find_node_by_name(g, srcq);
        if (f == -1){ printf("Source '%s' not found\n", srcq); graph_free(g); return 1; }
        src_idx = f;
    }

   
    char dst_req_type[64]; int dst_is_any = parse_any_keyword_strict(dstq, dst_req_type);

    int dst_idx = -1;
    if (dst_is_any){
       
        if (src_idx == -1){
           
            for (int i=0;i<g->V;i++){ if (node_matches_type_or_name(g,i,dst_req_type)){ dst_idx = i; break; } }
            if (dst_idx==-1){ printf("No facility of type '%s' found\n", dst_req_type); graph_free(g); return 1; }
            
            if (src_is_any){
                int src_chosen = find_nearest_of_type_from(g, dst_idx, src_req_type);
                if (src_chosen == -1){ printf("No facility of type '%s' found\n", src_req_type); graph_free(g); return 1; }
                src_idx = src_chosen;
                printf("Selected nearest %s as source: %s\n", src_req_type, g->name[src_idx] ? g->name[src_idx] : "(unnamed)");
            }
        } else {
           
            int chosen = find_nearest_of_type_from(g, src_idx, dst_req_type);
            if (chosen == -1){ printf("No facility of type '%s' found\n", dst_req_type); graph_free(g); return 1; }
            dst_idx = chosen;
            printf("Selected nearest %s as destination: %s\n", dst_req_type, g->name[dst_idx] ? g->name[dst_idx] : "(unnamed)");
        }
    } else {
        
        int f = find_node_by_name(g, dstq);
        if (f == -1){ printf("Destination '%s' not found\n", dstq); graph_free(g); return 1; }
        dst_idx = f;
        
        if (!type_is_allowed(g->type[dst_idx]) && !node_matches_type_or_name(g, dst_idx, "hospital") && !node_matches_type_or_name(g, dst_idx, "fire") && !node_matches_type_or_name(g, dst_idx, "police")){
            printf("Destination '%s' is not a hospital/fire/police type (its type: '%s')\n", g->name[dst_idx]?g->name[dst_idx]:"N/A", g->type[dst_idx]?g->type[dst_idx]:"N/A");
            graph_free(g); return 1;
        }
    }

    
    if (src_idx == -1 && src_is_any){
        int chosen = find_nearest_of_type_from(g, dst_idx, src_req_type);
        if (chosen == -1){ printf("No facility of type '%s' found\n", src_req_type); graph_free(g); return 1; }
        src_idx = chosen;
        printf("Selected nearest %s as source: %s\n", src_req_type, g->name[src_idx] ? g->name[src_idx] : "(unnamed)");
    }

    
    if (src_idx < 0 || dst_idx < 0){ printf("Could not resolve src/dst\n"); graph_free(g); return 1; }

    
    double *dist = malloc(sizeof(double) * g->V);
    int *parent = malloc(sizeof(int) * g->V);
    if (!dist || !parent){ perror("malloc"); graph_free(g); return 1; }
    dijkstra(g, src_idx, dist, parent);

    if (dist[dst_idx] >= INF/2){
        printf("No path found from '%s' to '%s'\n", g->name[src_idx]?g->name[src_idx]:"src", g->name[dst_idx]?g->name[dst_idx]:"dst");
    } else {
        printf("\nShortest travel time = %.1f seconds (%.2f minutes)\n", dist[dst_idx], dist[dst_idx]/60.0);
        printf("Route: "); print_path(g, parent, dst_idx); printf("\n");
    }

    free(dist); free(parent); graph_free(g); return 0;
}

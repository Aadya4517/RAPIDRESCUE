#include <gtkmm.h>
#include <iostream>
#include <vector>
#include <queue>
#include <map>
#include <cmath>
#include <algorithm>
#include <string>
#include <sstream>
#include <iomanip>

using namespace std;

// ------------------------ HAVERSINE ------------------------
double haversine(double lat1, double lon1, double lat2, double lon2) {
    const double R = 6371.0;
    const double p = 3.14159265358979323846 / 180.0;

    lat1 *= p; lon1 *= p;
    lat2 *= p; lon2 *= p;

    double d1 = lat2 - lat1;
    double d2 = lon2 - lon1;

    double a = sin(d1/2)*sin(d1/2) +
               cos(lat1)*cos(lat2) * sin(d2/2)*sin(d2/2);

    return 2 * R * atan2(sqrt(a), sqrt(1 - a));
}

// URL-encode (simple, safe for query params)
static std::string url_encode(const std::string &value) {
    std::ostringstream escaped;
    escaped.fill('0');
    escaped << std::hex << std::uppercase;

    for (unsigned char c : value) {
        if (isalnum(c) || c == '-' || c == '_' || c == '.' || c == '~') {
            escaped << c;
        } else if (c == ' ') {
            escaped << '+';
        } else {
            escaped << '%' << std::setw(2) << int(c);
        }
    }

    return escaped.str();
}

// ============================================================
//                   DISPATCH WINDOW CLASS
// ============================================================
class DispatchWindow : public Gtk::Window {
public:
    DispatchWindow();

private:
    // GUI
    Gtk::Box main_box{Gtk::ORIENTATION_VERTICAL};
    Gtk::Grid grid;
    Gtk::Label lbl_title, lbl_location, lbl_emergency, lbl_severity, lbl_status;
    Gtk::ComboBoxText cb_location;
    Gtk::ComboBoxText cb_emergency, cb_severity;
    Gtk::Button btn_dispatch, btn_route;
    Gtk::Box button_box{Gtk::ORIENTATION_HORIZONTAL};

    // Data
    struct Station {
        std::string name;
        double lat, lon;
        std::string type; // "hospital", "police", "fire"
    };

    std::map<std::string, Station> stations;
    std::map<std::string, std::pair<double,double>> location_coords;

    // Graph
    std::map<std::string,int> node_index;
    std::vector<std::string> index_node;
    std::vector<std::vector<std::pair<int,double>>> graph;  // (to, weight)
    std::vector<int> parent;

    // Last dispatch
    std::string last_location;
    std::string nearest_hospital, nearest_fire, nearest_police;

    // Methods
    void build_graph();
    std::pair<double,double> get_coordinates(const std::string& name);
    std::vector<double> dijkstra(int start);
    std::vector<int> build_path(int start, int goal);

    // Buttons
    void on_dispatch_clicked();
    void on_route_clicked();
};

// ============================================================
//                       CONSTRUCTOR
// ============================================================
DispatchWindow::DispatchWindow() {
    set_title("Emergency Dispatch System (Dijkstra)");
    set_default_size(700, 560);
    add(main_box);

    lbl_title.set_markup("<span size='xx-large' weight='bold'>🚒 Emergency Dispatch</span>");
    lbl_title.set_margin_bottom(20);
    lbl_title.set_halign(Gtk::ALIGN_CENTER);
    main_box.pack_start(lbl_title, Gtk::PACK_SHRINK);

    // GUI Layout
    grid.set_row_spacing(10);
    grid.set_column_spacing(15);

    lbl_location.set_text("Current Location:");
    grid.attach(lbl_location, 0, 0);
    grid.attach(cb_location, 1, 0);

    lbl_emergency.set_text("Emergency Type:");
    cb_emergency.append("Fire");
    cb_emergency.append("Accident");
    cb_emergency.append("Crime");
    cb_emergency.append("Medical Emergency");
    cb_emergency.set_active(0);
    grid.attach(lbl_emergency, 0, 1);
    grid.attach(cb_emergency, 1, 1);

    lbl_severity.set_text("Severity:");
    for(int i=1;i<=5;i++) cb_severity.append(std::to_string(i));
    cb_severity.set_active(0);
    grid.attach(lbl_severity, 0, 2);
    grid.attach(cb_severity, 1, 2);

    main_box.pack_start(grid, Gtk::PACK_SHRINK);

    // Buttons
    btn_dispatch.set_label("🚓 Dispatch Units");
    btn_route.set_label("🗺 Show Route");

    btn_dispatch.signal_clicked()
        .connect(sigc::mem_fun(*this, &DispatchWindow::on_dispatch_clicked));
    btn_route.signal_clicked()
        .connect(sigc::mem_fun(*this, &DispatchWindow::on_route_clicked));

    button_box.set_spacing(20);
    button_box.pack_start(btn_dispatch);
    button_box.pack_start(btn_route);
    main_box.pack_start(button_box, Gtk::PACK_SHRINK);

    lbl_status.set_text("System ready.");
    main_box.pack_start(lbl_status, Gtk::PACK_SHRINK);

    // ----------- Load Stations -----------------
    // Updated with exact coordinates you provided
    stations = {
        // Hospitals
        {"Shri Mahant Indiresh Hospital", {"Shri Mahant Indiresh Hospital", 30.3047, 78.0207, "hospital"}},
        {"Panacea Hospital Dehradun",      {"Panacea Hospital Dehradun",     30.3175, 78.0260, "hospital"}},
        {"Max Super Speciality Hospital",  {"Max Super Speciality Hospital", 30.3829, 78.0891, "hospital"}},
        {"Synergy Hospital",               {"Synergy Hospital",              30.3375, 78.0136, "hospital"}},

        // Police
        {"Clement Town Police Station",   {"Clement Town Police Station",  30.3156, 78.0361, "police"}},
        {"ISBT Police Chowki",            {"ISBT Police Chowki",           30.2884, 77.9972, "police"}},
        {"Ghanta Ghar Police Chowki",     {"Ghanta Ghar Police Chowki",    30.3240, 78.0416, "police"}},
        {"Rajpur Police Station",         {"Rajpur Police Station",        30.3631, 78.0683, "police"}},

        // Fire
        {"Dehradun Fire Station",         {"Dehradun Fire Station",        30.3165, 78.0322, "fire"}},
        {"Rajpur Road Fire Station",      {"Rajpur Road Fire Station",     30.3371, 78.0528, "fire"}}
    };

    // ----------- Load Locations -----------------
    location_coords = {
        {"Graphic Era University", {30.3196, 78.0413}},
        {"Clement Town",           {30.315, 78.035}},
        {"ISBT",                   {30.317, 78.028}},
        {"Clock Tower",            {30.325, 78.040}},
        {"Rajpur Road",            {30.353, 78.075}},
        {"Subhash Nagar",          {30.317, 78.030}}
    };

    // Populate location combobox so user selects an exact name (so it can be pasted/encoded)
    for (auto &p : location_coords) {
        cb_location.append(p.first);
    }
    cb_location.set_active(0);

    build_graph();
    show_all_children();
}

// ============================================================
//                          GRAPH
// ============================================================
std::pair<double,double> DispatchWindow::get_coordinates(const std::string& name){
    if(location_coords.count(name)) return location_coords[name];
    if(stations.count(name)) return {stations.at(name).lat, stations.at(name).lon};
    return {0,0};
}

void DispatchWindow::build_graph() {
    node_index.clear();
    index_node.clear();

    // Ensure deterministic insertion order: insert locations first, then stations
    for(auto& p : location_coords){
        node_index[p.first] = index_node.size();
        index_node.push_back(p.first);
    }
    for(auto& s : stations){
        node_index[s.first] = index_node.size();
        index_node.push_back(s.first);
    }

    int N = index_node.size();
    graph.assign(N, {});

    auto add_road = [&](const std::string& A, const std::string& B){
        if(!node_index.count(A) || !node_index.count(B)) return;
        auto [latA, lonA] = get_coordinates(A);
        auto [latB, lonB] = get_coordinates(B);
        double d = haversine(latA, lonA, latB, lonB);

        int a = node_index[A];
        int b = node_index[B];

        graph[a].push_back({b, d});
        graph[b].push_back({a, d});
    };

    // Connect locations among themselves (simple local graph)
    add_road("Graphic Era University", "Clement Town");
    add_road("Clement Town", "ISBT");
    add_road("ISBT", "Clock Tower");
    add_road("Clock Tower", "Graphic Era University");
    add_road("Clock Tower", "Rajpur Road");
    add_road("ISBT", "Subhash Nagar");
    add_road("Subhash Nagar", "Graphic Era University");

    // Connect stations to nearby location nodes (so Dijkstra can reach them realistically)
    add_road("Shri Mahant Indiresh Hospital", "ISBT");
    add_road("Shri Mahant Indiresh Hospital", "Subhash Nagar");
    add_road("Panacea Hospital Dehradun", "ISBT");
    add_road("Synergy Hospital", "Clock Tower");
    add_road("Max Super Speciality Hospital", "Rajpur Road");

    add_road("Clement Town Police Station", "Clement Town");
    add_road("ISBT Police Chowki", "ISBT");
    add_road("Ghanta Ghar Police Chowki", "Clock Tower");
    add_road("Rajpur Police Station", "Rajpur Road");

    add_road("Dehradun Fire Station", "Clement Town");
    add_road("Dehradun Fire Station", "Clock Tower");
    add_road("Dehradun Fire Station", "Rajpur Road");
    add_road("Rajpur Road Fire Station", "Rajpur Road");
}

// ============================================================
//                       DIJKSTRA
// ============================================================
std::vector<double> DispatchWindow::dijkstra(int start){
    int n = graph.size();
    std::vector<double> dist(n, 1e18);
    parent.assign(n, -1);

    using P = std::pair<double,int>;
    std::priority_queue<P, std::vector<P>, std::greater<P>> pq;

    dist[start] = 0;
    pq.push({0,start});

    while(!pq.empty()){
        auto [d,u] = pq.top(); pq.pop();
        if(d != dist[u]) continue;

        for(auto &e : graph[u]){
            int v = e.first;
            double w = e.second;
            if(dist[v] > d + w){
                dist[v] = d + w;
                parent[v] = u;
                pq.push({dist[v], v});
            }
        }
    }
    return dist;
}

std::vector<int> DispatchWindow::build_path(int start, int goal) {
    std::vector<int> path;
    for(int v = goal; v != -1; v = parent[v])
        path.push_back(v);
    std::reverse(path.begin(), path.end());
    return path;
}

// ============================================================
//                  DISPATCH BUTTON
// ============================================================
void DispatchWindow::on_dispatch_clicked() {
    std::string loc = cb_location.get_active_text();

    if(!node_index.count(loc)){
        lbl_status.set_text("Unknown location.");
        return;
    }

    int start = node_index[loc];
    auto dist = dijkstra(start);

    double bestH = 1e18, bestF = 1e18, bestP = 1e18;
    nearest_hospital.clear(); nearest_fire.clear(); nearest_police.clear();

    for(auto& sp : stations){
        const auto &s = sp.second;
        int id = node_index[s.name];

        if(s.type == "hospital" && dist[id] < bestH){
            bestH = dist[id];
            nearest_hospital = s.name;
        }
        if(s.type == "fire" && dist[id] < bestF){
            bestF = dist[id];
            nearest_fire = s.name;
        }
        if(s.type == "police" && dist[id] < bestP){
            bestP = dist[id];
            nearest_police = s.name;
        }
    }

    last_location = loc;

    std::ostringstream stat;
    stat << "Dispatched from " << loc << ": ";
    stat << "Hospital=" << (nearest_hospital.empty()?"(none)":nearest_hospital) << ", ";
    stat << "Police=" << (nearest_police.empty()?"(none)":nearest_police) << ", ";
    stat << "Fire=" << (nearest_fire.empty()?"(none)":nearest_fire) << ".";

    lbl_status.set_text(stat.str());
}

// ============================================================
//                ROUTE BUTTON (GOOGLE MAPS)
// ============================================================
void DispatchWindow::on_route_clicked() {
    if(last_location.empty()){
        lbl_status.set_text("Dispatch first!");
        return;
    }

    Glib::ustring g_em = cb_emergency.get_active_text();
    std::string emergency = g_em.raw();

    std::string dest;

    if(emergency == "Fire") dest = nearest_fire;
    else if(emergency == "Crime" || emergency == "Accident") dest = nearest_police;
    else dest = nearest_hospital; // Medical Emergency

    if(dest.empty()){
        lbl_status.set_text("No suitable destination found.");
        return;
    }

    std::string origin_text = url_encode(last_location);
    std::string dest_text = url_encode(dest);

    std::string url = "https://www.google.com/maps/dir/?api=1&origin=" +
                 origin_text + "&destination=" + dest_text;

#ifdef _WIN32
    std::string cmd = "start \"\" \"" + url + "\"";
    system(cmd.c_str());
#else
    std::string cmd = "xdg-open \"" + url + "\"";
    system(cmd.c_str());
#endif

    lbl_status.set_text("Opening route in browser...");
}

// ============================================================
//                          MAIN
// ============================================================
int main(int argc, char *argv[]) {
    auto app = Gtk::Application::create(argc, argv, "org.gtkmm.dispatch");
    DispatchWindow window;
    return app->run(window);
}

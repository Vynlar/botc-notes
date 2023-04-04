(ns app.todo-list
  (:require contrib.str
            #?(:clj [datascript.core :as d]) ; database on server
            [app.admin-panel :as admin]
            [hyperfiddle.electric :as e]
            [contrib.electric-goog-history :refer [Link path]]
            [hyperfiddle.electric-dom2 :as dom]
            [hyperfiddle.electric-ui4 :as ui]
            [clojure.string :as s]))

#?(:clj (defonce !conn (d/create-conn {}))) ; database on server
(e/def db) ; injected database ref; Electric defs are always dynamic

(e/defn TodoItem [id]
  (e/server
   (let [e (d/entity db id)
         status (:task/status e)]
     (e/client
      (dom/div
       (ui/checkbox
        (case status :active false, :done true)
        (e/fn [v]
          (e/server
           (d/transact! !conn [{:db/id id
                                :task/status (if v :done :active)}])
           nil))
        (dom/props {:id id}))
       (dom/label (dom/props {:for id}) (dom/text (e/server (:task/description e)))))))))

(e/defn InputSubmit [F]
  ; Custom input control using lower dom interface for Enter handling
  (dom/input (dom/props {:placeholder "Buy milk"})
             (dom/on "keydown" (e/fn [e]
                                 (when (= "Enter" (.-key e))
                                   (when-some [v (contrib.str/empty->nil (-> e .-target .-value))]
                                     (new F v)
                                     (set! (.-value dom/node) "")))))))

(e/defn TodoCreate []
  (e/client
   (InputSubmit. (e/fn [v]
                   (e/server
                    (d/transact! !conn [{:task/description v
                                         :task/status :active}])
                    nil)))))

#?(:clj (defn todo-count [db]
          (count
           (d/q '[:find [?e ...] :in $ ?status
                  :where [?e :task/status ?status]] db :active))))

#?(:clj (defn todo-records [db]
          (->> (d/q '[:find [(pull ?e [:db/id :task/description]) ...]
                      :where [?e :task/status]] db)
               (sort-by :task/description))))

(defn parse-route [path] path)

(e/defn Todo-list []
  (e/client
   (let [route (parse-route path)]
     (cond (= route "/") (do (dom/h1 (dom/text "Home")) (Link. "/a" (dom/text "a")))
           (= route "/a") (do (dom/h1 (dom/text "A")) (Link. "/" (dom/text "home")))
           (= route "/admin") (admin/Admin-panel.)
           (s/starts-with? route "/admin/game/") (let [game-id (nth (s/split route "/") 3)]
                                                   (admin/Game-admin. game-id))
           (s/starts-with? route "/game/") (let [game-id (nth (s/split route "/") 2)]
                                             (admin/Game-player. game-id))
           #_#_(= route "/todo") (e/server
                                  (binding [db (e/watch !conn)]
                                    (e/client
                                     (dom/link (dom/props {:rel :stylesheet :href "/todo-list.css"}))
                                     (dom/h1 (dom/text "minimal todo list"))
                                     (dom/p (dom/text "it's multiplayer, try two tabs"))
                                     (dom/div (dom/props {:class "todo-list"})
                                              (TodoCreate.)
                                              (dom/div {:class "todo-items"}
                                                       (e/server
                                                        (e/for-by :db/id [{:keys [db/id]} (todo-records db)]
                                                                  (TodoItem. id))))
                                              (dom/p (dom/props {:class "counter"})
                                                     (dom/span (dom/props {:class "count"}) (dom/text (e/server (todo-count db))))
                                                     (dom/text " items left"))))))

           :else (dom/h1 (dom/text "no matching route: " (pr-str path)))))))

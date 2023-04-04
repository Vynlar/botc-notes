(ns app.admin-panel
  (:require contrib.str
            #?(:clj [datascript.core :as d]) ; database on server
            [hyperfiddle.electric :as e]
            [hyperfiddle.rcf :refer [tests]]
            [contrib.electric-goog-history :refer [parse-route path !history]]
            [hyperfiddle.electric-dom2 :as dom]
            [hyperfiddle.electric-ui4 :as ui]))

#?(:clj (defonce !games (atom {})))

(defn new-game []
  {:game/id (str (random-uuid))
   :game/phase [:setup]
   :game/players
   (into []
         (for [id (range 7)]
           {:player/name (str "Player " (inc id))
            :player/character nil}))})

(defn insert-game [games game]
  (swap! !games assoc (:game/id game) game))

(defn inc-phase [phase]
  (case (first phase)
    :setup [:night 1]
    :night [:day (second phase)]
    :day [:night (inc (second phase))]
    :win phase))

(e/defn Link [href label]
  (e/client
   (dom/a (dom/props {:href href})
          (dom/on "click" (e/fn [e]
                            (.setToken !history href)
                            (.preventDefault e)))
          (dom/text label))))

(e/def games)

(e/defn Game-phase [game-phase]
  (e/client
   (dom/div
    (case (first game-phase)
      :setup (dom/text "Setup phase")
      :day (dom/text "Day " (second game-phase))
      :night (dom/text "Night " (second game-phase))
      :win (dom/text (name (second game-phase)) " won!")))))

(e/defn New-game-button []
  (dom/button (dom/on "click"
                      (e/fn [event] (e/server
                                     (let [game (new-game)
                                           game-id (:game/id game)
                                           game-url (str "/admin/game/" game-id)]
                                       (insert-game !games game)
                                       (e/client
                                        (.setToken !history game-url))))))
              (dom/text "New game")))

(e/defn Game-admin [game-id]
  (e/server
   (let [{:game/keys [id players phase]} (get (e/watch !games) game-id)]
     (e/client
      (dom/div
       (New-game-button.)
       (dom/text "Game id: " id)
       (Game-phase. phase)

       (when (not= :win (first phase))
         (dom/button (dom/on "click" (e/fn [_]
                                       (e/server
                                        (swap! !games update-in [game-id :game/phase] inc-phase))))
                     (dom/text "Next phase"))

         (dom/button (dom/on "click" (e/fn [_]
                                       (e/server
                                        (swap! !games assoc-in [game-id :game/phase] [:win :good]))))
                     (dom/text "Good won"))

         (dom/button (dom/on "click" (e/fn [_]
                                       (e/server
                                        (swap! !games assoc-in [game-id :game/phase] [:win :evil]))))
                     (dom/text "Evil won")))

       (dom/ul
        (e/for [{:player/keys [name character]} players]
          (dom/li (dom/text name (str " (" character ")"))))))))))

(e/defn Game-player [game-id]
  (e/server
   (let [{:game/keys [id players phase]} (get (e/watch !games) game-id)]
     (e/client
      (dom/div
       (dom/text "Game id: " id " Welcome!")
       (Game-phase. phase))))))

(e/defn Admin-panel []
  (e/client
   (New-game-button.)
   (dom/ul
    (e/server
     (binding [games (e/watch !games)]
       (e/for [game-id (keys games)]
         (e/client
          (dom/li (dom/text game-id) (Link. (str "/admin/game/" game-id) "Join")))))))))

(defn reset-games! []
  (reset! !games {}))

(comment

  (reset-games!)
  (swap! !games (fn [games]
                  (assoc games (str "game-" (inc (count (keys games)))) {})))
  (swap! !games (fn [games] (assoc games (str "game-" (count (keys games))) {})))
  (keys @!games))

(ns wacnet.views.templates)

(defn dropdown-content [content]
  (conj [:ul.dropdown-menu] content))

(defn dropdown [title & content]
  [:li.dropdown {:style "cursor: pointer"}
   [:a.dropdown-toggle {:data-toggle "dropdown"}
    title [:b.caret]]
   (dropdown-content content)])

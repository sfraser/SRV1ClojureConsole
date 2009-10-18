(comment
 Examples of talking to Surveyor SRV-1 robot form the REPL
 Make sure you have first run the clojure code in SRV1ClojureConsole so that you have an active connection.
 )
(ns org.frasers.srv1.SRV1ClojureConsole)

; cd /Users/sfraser/NetBeansProjects/SRV1ClojureConsole/dist/
; java -cp SRV1ClojureConsole.jar:lib/clojure-1.0.0.jar clojure.main


; playing with lasers
(sendCommandToRobot "l")
(sendCommandToRobot "L")

; We need to do a lot of sleeping, let's make it easy
(defn sleep
  ([x] (Thread/sleep x))
  ([] (sleep 100)))

; Lasers on
(defn lazon[] (sendCommandToRobot "l")(sleep))
;(class lazon)
;(lazon)

; Lasers off
(defn lazoff[] (sendCommandToRobot "L")(sleep))
;(lazoff)

; Flash em
;(do (lazon)(lazoff))

; Flash 10 times
;(dotimes [i 10] (lazon)(lazoff))

; Make it a new functions
(defn flash
  ([]
    (dotimes [i 10] (lazon)(lazoff)))
  ([x]
    (dotimes [i x] (lazon)(lazoff))))

; spin left and right
(defn left[] (sendCommandToRobot "0")(sleep))
(defn right[] (sendCommandToRobot ".")(sleep))

; forward and back
(defn forward
  ([] (sendCommandToRobot "8"))
  ([x] (forward) (Thread/sleep x) (stop)))

(defn back
  ([] (sendCommandToRobot "2"))
  ([x] (back)(Thread/sleep x) (stop)))

(defn stop[] (sendCommandToRobot "5")(sleep))

(defn dance[]
  (forward 500)
  (flash 1)
  (back 500)
  (flash 1)
  (forward 500)
  (flash 1)
  (back 500)
  (flash)
  (driftleft)
  (driftright)
  (flash 10)
  (backleft)
  (backright)
  (flash 10)
  (dotimes [i 10] (right)(flash 1)(right)(flash 1)(left)(flash 1)))


(defn battery[] (sendCommandToRobot "D"))

(defn driftleft[] (sendCommandToRobot "4D283C00" encoding_HEX)(sleep)(stop))
(defn driftright[] (sendCommandToRobot "4D3C2800" encoding_HEX)(sleep)(stop))

(defn backleft[] (sendCommandToRobot "4DE2BA00" encoding_HEX)(sleep)(stop))
(defn backright[] (sendCommandToRobot "4DBAE200" encoding_HEX)(sleep)(stop))
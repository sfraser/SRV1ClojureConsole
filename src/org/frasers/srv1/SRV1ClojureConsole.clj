(comment
 Clojure based console for Surveyor SRV-1 robot
 )
(ns org.frasers.srv1.SRV1ClojureConsole
  (:gen-class))

(import 
  '(java.awt BorderLayout FlowLayout Color)
  '(java.awt.event ActionListener KeyListener KeyEvent)
  '(javax.swing JFrame JTextField JButton JTextArea JPanel JComboBox JScrollPane JSplitPane)
  '(javax.swing.event ChangeListener)
  '(java.util ArrayList)
  '(net.miginfocom.swing MigLayout)
  '(org.frasers.srv1 JpegRenderer SRV1Test NetworkSRV1Reader SRV1CommandCallback))

(def encoding_ASCII "ASCII")
(def encoding_HEX "HEX")

(defn buildCommand [text encoding]
  (if (= encoding encoding_HEX)
    (.toByteArray (BigInteger. text 16))
    (. text getBytes "US-ASCII")))

(declare srv1)
(declare commandLog)

(defn sendCommandToRobot
  ([cmd]
      (sendCommandToRobot cmd encoding_ASCII))
  ([cmd encoding]
      (. srv1 sendCommand cmd (buildCommand cmd encoding)
        (proxy [SRV1CommandCallback] []
          (success [cmdString response]
            ;(println (format "sendcommand: %s response: %s" cmdString response))
            (. commandLog append (format "[%s] response: %s %n" cmdString response))
            (. commandLog setCaretPosition (.. commandLog getText length)))
          (failure [cmdString]
            ;(println (format "FAILED sendcommand: %s" cmdString))
            (. commandLog append (format "FAILED sendcommand: %s" cmdString))
            (. commandLog setCaretPosition Integer/MAX_VALUE))))))


; cmd2 is optional - we will fire the command on button up if it passed in
(defn makeCommandButton [label [cmd1 cmd2]]
  (let [pb (JButton. label)
        model (.getModel pb)
        down #(and (.isArmed model) (.isPressed model))
        up   #(and (not (.isArmed model)) (not (.isPressed model))) ]
    (doto pb
      (.addChangeListener
        (reify ChangeListener
          (stateChanged [_ _]
            (when (down)
              (sendCommandToRobot cmd1))
            (when (and cmd2 (up))
              (sendCommandToRobot cmd2))))))))

(defn convertHexToBytesInString [strHex]
  (String. (.toByteArray (BigInteger. strHex 16))))


; [ label [cmd1 cmd2] optionalMigString]
(def srv1LabelsAndControlProtocol
  [["Lasers ON" ["l"] "cell 0 0"]
   ["Lasers OFF" ["L"]"cell 0 1"]
   ;    we now send the Init command automatically at startup
   ;    ["Init" (convertHexToBytesInString "4D00FF14")]
   ["<-" ["0"] "cell 1 1"]
   ["STOP" ["5"] "cell 2 1"]
   ["->" ["."] "cell 3 1"]
   ["Fwd" ["8" "5"] "cell 2 0"]
   ["Back" ["2" "5"]"cell 2 2"]
   ["LoRez" [(convertHexToBytesInString "62")]]
   ["HiRez" [(convertHexToBytesInString "63")]]
   ["HD" [(convertHexToBytesInString "41")]]
   ["+" [(convertHexToBytesInString "2B")]]
   ["-" [(convertHexToBytesInString "2D")]]

   ;    ["L 20" (convertHexToBytesInString "4D007F14" )]
   ;    ["R 20" (convertHexToBytesInString "4DFF0014" )]
   ;    ["F" (convertHexToBytesInString "4D323214" )]
   ;    ["B" (convertHexToBytesInString "4DCECE14" )]
   ;    ["L 20" "4D00FF14"]
   ;    ["R 20" "4DFF0014"]
   ;    ["F" "4D323214"]
   ;    ["B" "4DCECE14"]
   ["Range" ["R"]]
   ])

(defn -main[]

  (let [f (JFrame. "SRV-1 Console - Clojure")
        jpegRenderer (doto (JpegRenderer. f) (.setSize 320 240))
        frameListeners (doto (ArrayList.) (.add jpegRenderer))
        commandField (JTextField. 20)
        sendButton (JButton. "Send")
        textAreaForCommands (var-get (def commandLog (JTextArea. "" 5 10)))
        encoding (JComboBox. (to-array [encoding_ASCII encoding_HEX]))
        sendCommandAction (proxy [ActionListener] []
                            (actionPerformed [event]
                              (let [commands (.. commandField getText (split SRV1Test/CMD_DELIM))]
                                (doseq [untrimmedCommand (seq commands)]
                                  (sendCommandToRobot (.trim untrimmedCommand) (.getSelectedItem encoding))))))

        pCmdLine (doto (JPanel.) (.add encoding) (.add commandField) (.add sendButton))
        pMain (doto (JPanel. (BorderLayout.)) (.add "North" pCmdLine) (.add "Center" (JScrollPane. textAreaForCommands)))

        pCmdButtons (JPanel. (MigLayout. "" "[100:pref,fill]" "[100:pref,fill]"))
        pConsoleAndButtons (doto (JPanel. (BorderLayout.)) (.add "Center" jpegRenderer) (.add "South" pCmdButtons))

        splitPane (doto (JSplitPane. JSplitPane/HORIZONTAL_SPLIT) 
                    (.setLeftComponent pConsoleAndButtons)
                    (.setRightComponent pMain))
        ]

    ; set our global ref to the robot
    (def srv1 (NetworkSRV1Reader. SRV1Test/SRV_HOST SRV1Test/SRV_PORT SRV1Test/SRV_PROTOCOL frameListeners))


    (doseq [[label cmds migString] srv1LabelsAndControlProtocol]
      (. pCmdButtons add (makeCommandButton label cmds) migString))

    (. sendButton addActionListener sendCommandAction)

    (. commandField addKeyListener (proxy [KeyListener] []
                                     (keyPressed [e])
                                     (keyTyped [e])
                                     (keyReleased [e] (if (identical? (.getKeyCode e) KeyEvent/VK_ENTER)
                                                        (.actionPerformed sendCommandAction)))))

    (doto f
      (.setBackground Color/WHITE)
      (.setLayout (BorderLayout. 3 3))
      (.add "Center" splitPane)
      ;(.add "South" pMain)
      (.setDefaultCloseOperation JFrame/EXIT_ON_CLOSE)
      (.pack)
      (.setVisible true))


    (.start srv1)
    (Thread/sleep 100)
    ; according to the Surveyor protocol, we need to initialize the motors
    ; to some speed before the "robot drive" commands will work. In other
    ; words we need to tell the motors how fast they should turn once
    ; they are told to turn on. So at bootup these values are probably set
    ; to zero. Since we don't know if they are set yet, we go ahead and
    ; do this once to be sure
    (sendCommandToRobot "4D00FF14" encoding_HEX)))



(-main)
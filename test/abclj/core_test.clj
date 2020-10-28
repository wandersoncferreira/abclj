(ns abclj.core-test
  (:import [org.armedbear.lisp Lisp LispObject LispInteger Cons StandardObject]
           [abclj.java UnhandledCondition])
  (:require [clojure.test :refer :all]
            [abclj
             [core :refer :all]
             [readers :refer :all]]))

(deftest with-cl-test
  (testing
    (is (instance? LispInteger (with-cl '(progn 1))))
    (is (instance? Cons (with-cl '(progn '(1 . 2)
                                         '(3 . 4)))))
    (is (instance? StandardObject (with-cl
                                    '(progn
                                       (defclass ellipse ()
                                         ((h-axis :type real :accessor h-axis :initarg :h-axis)
                                          (v-axis :type real :accessor v-axis :initarg :v-axis)))
                                       (defclass circle (ellipse)
                                         ((radius :type real :accessor radius :initarg :radius)))
                                       (defmethod initialize-instance ((c circle) &key radius)
                                         (setf (radius c) radius))
                                       (defmethod (setf radius) :after ((new-value real) (c circle))
                                         (setf (slot-value c 'h-axis) new-value
                                               (slot-value c 'v-axis) new-value))
                                       (defmethod (setf h-axis) :after ((new-value real) (c circle))
                                         (unless (= (radius c) new-value)
                                                 (change-class c 'ellipse)))
                                       (defmethod (setf v-axis) :after ((new-value real) (c circle))
                                         (unless (= (radius c) new-value)
                                                 (change-class c 'ellipse)))
                                       (defmethod initialize-instance :after ((e ellipse) &key h-axis v-axis)
                                         (if (= h-axis v-axis)
                                           (change-class e 'circle)))
                                       (defmethod (setf h-axis) :after ((new-value real) (e ellipse))
                                         (unless (subtypep (class-of e) 'circle)
                                                 (if (= (h-axis e) (v-axis e))
                                                   (change-class e 'circle))))
                                       (defmethod (setf v-axis) :after ((new-value real) (e ellipse))
                                         (unless (subtypep (class-of e) 'circle)
                                                 (if (= (h-axis e) (v-axis e))
                                                   (change-class e 'circle))))
                                       (defmethod update-instance-for-different-class :after ((old-e ellipse)
                                                                                              (new-c circle) &key)
                                         (setf (radius new-c) (h-axis old-e))
                                         (unless (= (h-axis old-e) (v-axis old-e))
                                                 (error "ellipse ~s can't change into a circle because it's not one!"
                                                        old-e)))))))))

(deftest ->bool-test
  (testing "Everything that is not cl-nil should be true"
    (is (->bool #abclj/cl-integer 1))
    (is (->bool #abclj/cl-double 2.0))
    (is (->bool #abclj/cl-ratio 2/3))
    (is (->bool #abclj/cl-complex [1 1]))
    (is (->bool #abclj/cl-string "abc"))
    (is (->bool (cl-symbol :test)))
    (is (->bool (cl-symbol 'test)))
    (is (false? (->bool cl-nil)))))

(deftest with-cl->clj-test
  (testing
    (is (= 120 (with-cl->clj
                 '(defun fact (n)
                    (reduce (function *) (loop for i from 1 to n collect i)))
                 '(fact 5))))))

(deftest set-get-var-test
  (testing
    (is (= 123 (cl->clj (do (setvar 'test #abclj/cl-integer 123)
                            (getvar 'test)))))))

(deftest set-get-function-test
  (testing
    (let [+-cl-func (-> Lisp/PACKAGE_CL
                        (.findAccessibleSymbol "+")
                        .getSymbolFunction)]
      (is (= 3 (cl->clj (.execute +-cl-func #abclj/cl-integer 1 #abclj/cl-integer 2)))))
    (is (= -1.0 (cl->clj (.-realpart (funcall (getfunction 'cl/expt)
                                              #abclj/cl-complex [0 1]
                                              #abclj/cl-integer 2)))))))

(deftest alist->map-test
  (testing
    (is (= {:A "hue"
            :B "br"}
           (alist->map (with-cl '(progn '((:a . "hue")
                                          (:b . "br")))))))))

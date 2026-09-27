import unittest
from follow.geometry import Screen, transition

class GeometryTests(unittest.TestCase):
    def test_horizontal_both_ways(self):
        a, b = Screen("A", 0, 0, 100, 100), Screen("B", 100, 20, 200, 60)
        self.assertEqual(transition(a, [a,b], "right", .5), (b, 0, .5))
        self.assertEqual(transition(b, [a,b], "left", .5), (a, 1, .5))
    def test_vertical_and_offline(self):
        a, b = Screen("A", 0, 0, 100, 100), Screen("B", 20, 100, 60, 100)
        self.assertEqual(transition(a, [a,b], "down", .5), (b, .5, 0))
        self.assertIsNone(transition(a, [a,Screen("B",20,100,60,100,False)], "down", .5))
    def test_gap_and_noncontact(self):
        a, b = Screen("A", 0, 0, 100, 100), Screen("B", 105, 60, 100, 100)
        self.assertIsNone(transition(a,[a,b],"right",.3,10))
        self.assertIsNone(transition(a,[a,b],"right",.8))
        self.assertEqual(transition(a,[a,b],"right",.8,5), (b,0,.2))
    def test_dimensions_and_direction(self):
        with self.assertRaises(ValueError): Screen("X",0,0,0,1)
        with self.assertRaises(ValueError): transition(Screen("A",0,0,1,1), [], "diagonal", .5)

if __name__ == "__main__":
    unittest.main()

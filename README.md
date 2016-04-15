Run with ant

Algorithm so far goes through all of the instructions until it finds a stack operation
Runs the stack operation on the last two items pushed onto the stack
Deletes all three of these instructions (the operation, and the two pushes)
Replaces them with a single instruction to load the result from the operation on to the stack

Needs changing to work with variables, so store commands should be evaluated

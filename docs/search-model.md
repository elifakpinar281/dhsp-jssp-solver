# Search Model

The implementation follows the problem formulation described in
Artificial Intelligence: A Modern Approach (AIMA) by Stuart Russell and Peter Norvig.

## Goal formulation
Create a valid and, as far as possible, optimal schedule for the DHSP, with the
aim of minimizing makespan while satisfying all constraints.

## Problem formulation
A state can be viewed in two ways. Conceptually, a state is a partial schedule (the sequencing decisions made so far). 
In the implementation, this partial schedule is represented as a time-based simulation state that holds the information:

### State
- Current point in time
- Position of each crane
- Current bath of each product
- Which station/operation each product has already completed
- Occupied or free baths
- Remaining processing times
- Safety constraints (e.g. minimum distance between cranes, maximum load on a crane, etc.)
- Travel areas
- Maximum dwell times

### Actions
- Take product from conveyor
- Place product into bath
- Remove product from bath
- Move crane
- Have crane wait

## Search
The algorithm "simulates" the sequence of actions until it finds a solution.
It expands the states, following from the initial state to the goal state.

## Solution
The solution is the complete sequence of actions (a schedule).

## Execution
The cranes carry out this plan.

---

# Example

| Bath | Process               | Nominal time (processing) | Maximum time (max. dwell time) |
|------|-----------------------|---------------------------|--------------------------------|
| B1   | Degreasing            | 4                         | –                              |
| B2   | Rinsing               | 2                         | 3                              |
| B3   | Electroplating        | 6                         | 7                              |
| B4   | Rinsing / Passivating | 2                         | 3                              |

Two products, P1 and P2, both need to pass through this line.
Each bath can hold only one product at a time.
For this reason, P1 and P2 compete for the baths.

In what order and with what timing are the products guided through the stations so that the total time is minimized and the maximum dwell time is not exceeded?

## Job-shop abstraction
- One product means one job
- One bath means one machine (with nominal time and maximum dwell time)
- One bath run of a product means one operation


## Components of the search problem

### State space
A state is a partial schedule, i.e. which bath runs are already firmly scheduled, along with their ordering within the baths.
Example: P1 has occupied B1. All other bath runs are still open.


### Initial state
The initial state is the state where no bath runs have been scheduled yet.
All products are waiting to be processed and all baths are free.
P1 and P2 stand at the plant entrance and are waiting to be processed.

### Goal state (IS-GOAL)
Any state in which all bath runs of all products are scheduled and the plan is admissible (with respect to the maximum dwell time).
Multiple goal states exist, and not all of them are optimal. The search aims for the one that minimizes makespan.

### Actions (ACTIONS(s))
In a state s, ACTIONS(s) comprises all admissible operations whose predecessors within the same job are already completed or scheduled.
Each action extends the partial schedule by exactly one further operation.
If several operations are admissible at the same time, a scheduling decision must be made (e.g. with an SPT dispatching rule). Search methods such as A* consider
multiple alternatives.

In the DHSP, the actions are extended by transport decisions: in addition to the processing order, it must also be decided when and by which crane a product
is transported between two baths. This enlarges the set of possible actions.


### Transition model RESULT(s, "schedule P into bath")
The transition model returns the state in which the bath run is appended to the sequence of the target bath and its earliest admissible start time is set.

For the JSSP, this start time is the maximum of two values. The first is the point in time at which the product leaves the previous bath. The second is the
point in time at which the target bath becomes free after the previous product.


For the DHSP, two more aspects apply.

The first is crane availability and transport time. The start also depends on when a crane is free and how long the transport between the two baths takes. 
The earliest start is therefore the maximum of three values: the time the product is
available after being transported out of the previous bath, the time the target
bath becomes free, and the time a suitable crane becomes available.

The second is the maximum dwell time. Because the time a product may stay in a bath is limited, choosing the earliest possible start for one operation can make a
later operation infeasible. In that case the product would have to stay too long
in its current bath. The transition model must therefore also check that scheduling the operation does not break any maximum dwell time. Otherwise the
resulting state is not admissible.


### Action cost function ACTION-COST
The cost of an action is the increase in makespan:

````
c(s, a, s') = Cmax(s') - Cmax(s)
````

- c ... the cost function, meaning how expensive the action is 
- s ... the current state 
- a ... the action 
- s' ... the new state after the action has been applied 
- Cmax(s) ... the makespan of the current state s 
- Cmax(s') ... the makespan after the action, in the new state s'

The cost of an action is the increase in the current makespan. 
Because these increases build on each other, their sum along a path equals the makespan of the goal state and no action cost is negative. 

Optimization objectives, for example:
- Minimize makespan
- Do not break any maximum dwell time constraint
- No collisions
- Minimize transport time 
- Reduce product waiting time
- Improve utilization of the baths
  
Maximum dwell time, crane speed and transport time are treated as fixed constraints.
The waiting time of products in certain baths could later be reduced with a penalty term.
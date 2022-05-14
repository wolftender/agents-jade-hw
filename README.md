# agents homework jade
this is agents jade homework

## how to run
1. adjust the file `simulation.xml`
2. run with class `jade.Boot` and arguments `-gui simulation_agent:agents.SimulationAgent`
3. or spawn `agents.SimulationAgent` manually

## adjusting `simulation.xml`
this file is the "scenario engine", i.e., it tells the `SimulationAgent` what agents to spawn and what behaviours should be set up for them

this file needs to be placed in project root to be read by `SimulationAgent`

- `<restaruant>` tag corresponds to one `GatewayAgent` and one `ManagerAgent`
  - the parameter `reliability` means how many orders to reject, i.e. `reliability = 3` means reject every 3rd order
  - the parameter `maxPeople` means for how many people the restaurant can cook at once, i.e., if `maxPeople = 3` then every order for more than 3 people is rejected
    
- `<dish>` tag represents a dish served by restaurant
  - `id` of the dish tells what food it is and from what country it originates, to get the country id divide `id` by 100, e.g., dish 104 comes from country id 1, see `model.Cuisine`
    
- `<customer>` tag represents a `ClientAgent`, it will send orders given by its child `<order>` tags
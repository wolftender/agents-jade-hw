package agents;

import jade.core.Agent;
import jade.core.behaviours.OneShotBehaviour;
import jade.wrapper.AgentController;
import jade.wrapper.StaleProxyException;
import model.Customer;
import model.Restaurant;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import services.CustomerService;
import services.RestaurantService;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/*
 * simulation agent
 * the purpose of this agent is to create the "simulation world" i.e. initialize all the other agents
 * with whatever data ("scenario engine")
 */

public class SimulationAgent extends Agent {
    @Override
    protected void setup () {
        OneShotBehaviour loadSimulationBehaviour = new OneShotBehaviour () {
            private Restaurant parseRestaurant (Element node) {
                NodeList dishNodes = node.getElementsByTagName ("dish");

                String name = node.getAttributeNode ("name").getTextContent ();
                int reliability = Integer.parseInt (node.getAttributeNode ("reliability").getTextContent ());
                int maxPeople = Integer.parseInt (node.getAttributeNode ("maxPeople").getTextContent ());

                Restaurant restaurant = new Restaurant (name, maxPeople, reliability);

                for (int i = 0; i < dishNodes.getLength (); ++i) {
                    Node dishNode = dishNodes.item (i);

                    int dishId = Integer.parseInt (dishNode.getAttributes ().getNamedItem ("id").getTextContent ());
                    float price = Float.parseFloat (dishNode.getAttributes ().getNamedItem ("price").getTextContent ());

                    restaurant.addDish (dishId, price);
                }

                return restaurant;
            }

            private Customer parseCustomer (Element node) {
                NodeList orderNodes = node.getElementsByTagName ("order");
                String name = node.getAttributeNode ("name").getTextContent ();

                Customer customer = new Customer (name);

                for (int i = 0; i < orderNodes.getLength (); ++i) {
                    Node orderNode = orderNodes.item (i);

                    int dishId = Integer.parseInt (orderNode.getAttributes ().getNamedItem ("dishId").getTextContent ());
                    int people = Integer.parseInt (orderNode.getAttributes ().getNamedItem ("forPeople").getTextContent ());

                    customer.addOrder (dishId, people);
                }

                return customer;
            }

            private String generateAgentName (String name) {
                return name.replace (' ', '_');
            }

            @Override
            public void action () {
                System.out.printf ("simulation agent %s will now load the simulation...\n", getName ());

                File configFile = new File("simulation.xml");
                DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance ();
                DocumentBuilder documentBuilder = null;

                try {
                    documentBuilder = documentBuilderFactory.newDocumentBuilder ();
                } catch (ParserConfigurationException exception) {
                    exception.printStackTrace ();
                }

                Document document = null;
                if (documentBuilder != null) {
                    try {
                        document = documentBuilder.parse (configFile);
                    } catch (IOException | SAXException exception) {
                        exception.printStackTrace ();
                    }
                }

                if (document != null) {
                    NodeList restaurants = document.getElementsByTagName ("restaurant");

                    for (int i = 0; i < restaurants.getLength (); ++i) {
                        Node node = restaurants.item (i);
                        Restaurant restaurant = parseRestaurant ((Element) node);

                        String restaurantId = Integer.toString (i);
                        RestaurantService.getInstance ().registerRestaurant (restaurantId, restaurant);

                        try {
                            AgentController controller = getContainerController ().createNewAgent (
                                    generateAgentName (restaurant.getName ()),
                                    "agents.GatewayAgent", new String [] { restaurantId }
                            );

                            controller.start ();
                        } catch (StaleProxyException exception) {
                            System.err.printf ("failed to create representative for %s restaurant\n", restaurant.getName ());
                            exception.printStackTrace ();
                        }
                    }

                    NodeList customers = document.getElementsByTagName ("customer");

                    for (int i = 0; i < customers.getLength (); ++i) {
                        Node node = customers.item (i);
                        Customer customer = parseCustomer ((Element) node);

                        String customerId = Integer.toString (i);
                        CustomerService.getInstance ().registerCustomer (customerId, customer);

                        try {
                            AgentController controller = getContainerController ().createNewAgent (
                                    generateAgentName (customer.getName ()),
                                    "agents.ClientAgent", new String [] { customerId }
                            );

                            controller.start ();
                        } catch (StaleProxyException exception) {
                            System.err.printf ("failed to create agent for customer %s\n", customer.getName ());
                            exception.printStackTrace ();
                        }
                    }
                }
            }
        };

        addBehaviour (loadSimulationBehaviour);
        super.setup ();
    }
}

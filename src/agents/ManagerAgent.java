package agents;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import model.Customer;
import model.Restaurant;
import services.RestaurantService;

import java.util.HashSet;
import java.util.Set;

public class ManagerAgent extends Agent {
    private final Set<String> acceptedOrders = new HashSet<> ();
    private int ordersCounter = 1;

    private String restaurantId;
    private Restaurant restaurant;
    private AID gatewayId;

    @Override
    protected void setup () {
        Object [] args = getArguments ();

        restaurantId = (String) args [0];
        restaurant = RestaurantService.getInstance ().getRestaurant (restaurantId);

        addBehaviour (new CyclicBehaviour () {
            private void handleHandshake (ACLMessage message) {
                ACLMessage reply = message.createReply ();

                if (message.getContent ().equals (restaurantId)) {
                    System.out.printf ("%s handshake on restaurant %s with %s\n", getName (), restaurantId, message.getSender ().getName ());
                    gatewayId = message.getSender ();

                    reply.setPerformative (ACLMessage.AGREE);
                    reply.setContent ("handshake_agree");
                } else {
                    System.out.printf ("%s incorrect handshake restaurant %s with %s\n", getName (), restaurantId, message.getSender ().getName ());

                    reply.setPerformative (ACLMessage.REFUSE);
                    reply.setContent ("handshake_refuse_id_mismatch");
                }

                send (reply);
            }

            private void handleOrderCfp (ACLMessage message) {
                ACLMessage reply = message.createReply ();

                Customer.Order order = Customer.Order.deserialize (message.getContent ());
                String orderId = message.getReplyWith ();

                if (order != null && message.getSender ().equals (gatewayId)) {
                    ordersCounter++;
                    Float price = restaurant.price (order.dish (), order.amount ());

                    if (ordersCounter % restaurant.getReliability () != 0 && price != null) {
                        // add this to potentially accepted orders
                        // this may be useful later
                        acceptedOrders.add (orderId);

                        System.out.printf ("%s agree to order %s (%s)\n", getName (), orderId, message.getContent ());

                        // send the agreement message
                        reply.setPerformative (ACLMessage.AGREE);
                        reply.setContent ("agree");
                    } else {
                        System.out.printf ("%s refuse order %s (%s)\n", getName (), orderId, message.getContent ());

                        reply.setPerformative (ACLMessage.REFUSE);
                        reply.setContent ("cannot_serve");
                    }
                } else {
                    System.out.printf ("%s didn't understand order %s (%s)\n", getName (), orderId, message.getContent ());

                    reply.setPerformative (ACLMessage.NOT_UNDERSTOOD);
                    reply.setContent ("invalid_format");
                }

                send (reply);
            }

            @Override
            public void action () {
                ACLMessage message = receive ();

                if (message != null) {
                    if (message.getPerformative () == ACLMessage.PROPOSE) {
                        if (message.getConversationId ().equals ("handshake")) {
                            try {
                                Thread.sleep (1000);
                            } catch (InterruptedException e) {
                                e.printStackTrace ();
                            }

                            handleHandshake (message);
                        } else if (message.getConversationId ().equals ("food_query")) {
                            handleOrderCfp (message);
                        }
                    }
                } else {
                    block ();
                }
            }
        });

        super.setup ();
    }
}

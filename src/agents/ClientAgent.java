package agents;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import model.Customer;
import services.CustomerService;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ClientAgent extends Agent {
    private static class RequestBehaviour extends Behaviour {
        private final AID [] sellers;
        private final List<AID> offers;

        private final Customer.Order order;

        private MessageTemplate template;
        private String replyWith;

        private long lastReplyTimestamp = 0;
        private int step = 0;

        public RequestBehaviour (Agent agent, Customer.Order order, AID [] sellers) {
            super(agent);

            this.order = order;
            this.sellers = sellers;

            offers = new ArrayList<> ();
        }

        @Override
        public void action () {
            ClientAgent clientAgent = (ClientAgent) getAgent ();
            switch (step) {
                case 0 -> {
                    ACLMessage message = new ACLMessage (ACLMessage.CFP);
                    for (AID seller : sellers) {
                        System.out.printf ("%s ordering %d (x%d) from %s\n", getAgent ().getName (), order.dish (), order.amount (), seller.getName ());
                        message.addReceiver (seller);
                    }

                    replyWith = String.format ("cfp-%s-%d", clientAgent.customerId, System.currentTimeMillis ());

                    message.setContent (order.serialize ());
                    message.setConversationId ("food_order");
                    message.setReplyWith (replyWith); // set unique identifier for this conversation

                    template = MessageTemplate.and (MessageTemplate.MatchConversationId ("food_order"), MessageTemplate.MatchInReplyTo (replyWith));

                    getAgent ().send (message);
                    lastReplyTimestamp = System.currentTimeMillis ();
                    step = 1;
                }

                case 1 -> {
                    ACLMessage message = getAgent ().receive (template);
                    long now = System.currentTimeMillis ();

                    if (message != null) {
                        lastReplyTimestamp = now;
                        String senderName = message.getSender ().getName ();

                        if (message.getPerformative () == ACLMessage.PROPOSE) {
                            System.out.printf ("%s received propose from %s\n", getAgent ().getName (), senderName);
                            offers.add (message.getSender ());
                        } else if (message.getPerformative () == ACLMessage.REFUSE) {
                            System.out.printf ("%s received refuse from %s\n", getAgent ().getName (), senderName);
                        } else if (message.getPerformative () == ACLMessage.NOT_UNDERSTOOD) {
                            System.out.printf ("%s received not understood from %s\n", getAgent ().getName (), senderName);
                        } else {
                            System.out.printf ("client %s did not understand response %s\n", getAgent ().getName (), message.getContent ());
                            ACLMessage reply = message.createReply ();

                            reply.setPerformative (ACLMessage.NOT_UNDERSTOOD);
                            reply.setContent ("invalid_performative");

                            getAgent ().send (reply);
                        }
                    } else {
                        // timeout
                        if (now - lastReplyTimestamp >= 3000) {
                            step = 2;
                        } else {
                            block (3500);
                        }
                    }
                }

                case 2 -> {
                    if (offers.size () > 0) {
                        System.out.printf ("%s listing my potential sellers:\n", getAgent ().getName ());

                        for (AID seller : offers) {
                            System.out.printf (" - %s\n", seller.getName ());
                        }

                        int selectedSeller = clientAgent.random.nextInt (offers.size ());
                        AID seller = offers.get (selectedSeller);

                        System.out.printf ("selected seller %s, sending order %s\n", seller.getName (), replyWith);

                        ACLMessage message = new ACLMessage (ACLMessage.REQUEST);

                        message.addReceiver (seller);
                        message.setConversationId ("food_buy");
                        message.setReplyWith (replyWith);
                        message.setContent (order.serialize ());

                        getAgent ().send (message);
                    }

                    step = 3;
                }
            }
        }

        @Override
        public boolean done () {
            return (step >= 3);
        }
    };

    private Customer customer = null;
    private Random random;
    private String customerId;

    @Override
    protected void setup () {
        Object [] args = getArguments ();
        customerId = (String) args [0];

        random = new Random ();
        customer = CustomerService.getInstance ().getCustomer (customerId);

        // every 4s customer makes their next pre programmed order
        Behaviour orderBehaviour = new TickerBehaviour (this, 4000) {
            private int nextOrder = 0;

            @Override
            protected void onTick () {
                List<Customer.Order> orders = customer.getOrders ();
                if (nextOrder < orders.size ()) {
                    // get the order data
                    Customer.Order order = orders.get (nextOrder);
                    String cuisineId = Integer.toString (order.dish () / 100);

                    // make the order here
                    DFAgentDescription template = new DFAgentDescription ();
                    ServiceDescription serviceDescription = new ServiceDescription ();

                    serviceDescription.setType ("cuisine" + cuisineId);
                    template.addServices (serviceDescription);

                    try {
                        DFAgentDescription [] result = DFService.search (getAgent (), template);
                        AID [] names = new AID [result.length];

                        for (int i = 0; i < result.length; ++i) {
                            names [i] = result [i].getName ();
                        }

                        addBehaviour (new RequestBehaviour (this.getAgent (), order, names));
                    } catch (FIPAException exception) {
                        System.err.printf ("failed to order %d\n", order.dish ());
                        exception.printStackTrace ();
                    }

                    nextOrder++;
                } else {
                    removeBehaviour (this);
                }
            }
        };

        System.out.printf ("a new client with name %s for %s was created\n", getName (), customer.getName ());

        addBehaviour (orderBehaviour);
        super.setup ();
    }
}

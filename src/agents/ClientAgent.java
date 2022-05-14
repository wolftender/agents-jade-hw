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

public class ClientAgent extends Agent {
    private static class RequestBehaviour extends Behaviour {
        private final AID [] sellers;
        private final Customer.Order order;

        private MessageTemplate template;

        private long lastReplyTimestamp = 0;
        private int step = 0;

        public RequestBehaviour (Agent agent, Customer.Order order, AID [] sellers) {
            super(agent);

            this.order = order;
            this.sellers = sellers;
        }

        @Override
        public void action () {
            switch (step) {
                case 0 -> {
                    ACLMessage message = new ACLMessage (ACLMessage.CFP);
                    for (AID seller : sellers) {
                        System.out.printf ("%s ordering %d (x%d) from %s\n", getAgent ().getName (), order.dish (), order.amount (), seller);
                        message.addReceiver (seller);
                    }

                    String replyWith = "cfp-" + System.currentTimeMillis ();

                    message.setContent (order.serialize ());
                    message.setConversationId ("food-order");
                    message.setReplyWith (replyWith); // set unique identifier for this conversation

                    getAgent ().send (message);

                    template = MessageTemplate.and (MessageTemplate.MatchConversationId ("food-order"), MessageTemplate.MatchInReplyTo (replyWith));

                    lastReplyTimestamp = System.currentTimeMillis ();
                    step = 1;
                }

                case 1 -> {
                    ACLMessage message = getAgent ().receive (template);
                    long now = System.currentTimeMillis ();

                    if (message != null) {
                        lastReplyTimestamp = now;

                        if (message.getPerformative () == ACLMessage.PROPOSE) {
                            System.out.printf ("%s received propose from %s\n", getAgent ().getName (), message.getSender ());
                        } else if (message.getPerformative () == ACLMessage.REFUSE) {
                            System.out.printf ("%s received refuse from %s\n", getAgent ().getName (), message.getSender ());
                        } else if (message.getPerformative () == ACLMessage.NOT_UNDERSTOOD) {
                            System.out.printf ("%s received not understood from %s\n", getAgent ().getName (), message.getSender ());
                        } else {
                            ACLMessage reply = message.createReply ();

                            reply.setPerformative (ACLMessage.NOT_UNDERSTOOD);
                            reply.setContent ("invalid-performative");

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
            }
        }

        @Override
        public boolean done () {
            return (step >= 2);
        }
    };

    private Customer customer = null;

    @Override
    protected void setup () {
        Object [] args = getArguments ();
        String customerId = (String) args [0];

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

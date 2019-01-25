# Research project - Saga Java library


Simple lightweight application showcasing the basic usage of saga-lib in the context of Microservice Architecture
 and eventual consistency between services.

Saga library used: https://github.com/Domo42/saga-lib

To run the app either run the Main.java from your favorite IDE or:
1. run
        ````
        ./gradlew build
        ````
2. go to distributions folder
        ```` 
        cd saga-lib-simple/build/distributions
        ````

3. extract the archive
        ```
        tar -xvf saga-lib-simple-x.x.x.tar
        ```
4. go inside the bin folder to find the OS specific start script
5. run the script
        ```
        ./saga-lib-simple
        ```
 
 To turn on the debug log, go to
    ```` 
     saga-lib-playground/saga-lib-simple/src/main/resources/log4j2.yml
    ```` 
 and change root log level from info to debug

# Saga Pattern
A microservices world is polyglot. It involves services written in different languages writing into different databases, not all of which will even understand the concept of ACID transactions, so distributed transactions and 2PC are not recommended. The Saga concept removes the need for a distributed transaction by ensuring that the transaction at each step of the business process has a defined compensating transaction. In this way, if the business process encounters an error condition and is unable to continue, it can execute the compensating transactions for the steps that have already completed. This undoes the work completed so far in the business process and maintains the consistency of the system. 

There are a couple of different ways to implement a saga transaction, but the two most popular are:

* **Events/Choreography:** when there is no central coordination, each service produces and listens to other service's events and decides if an action should be taken or not.
* **Command/Orchestration:** when a coordinator service is responsible for centralizing the saga's decision making and sequencing business logic.

This project focuses on Command/Orchestration approach, and in particular **Internally coordinated Saga**. This means
that we want to get coordination without any one service having the complete picture. So internal workflow idea is about not having a centralized service that manages orchestration 
but rather to make individual services responsible for owning a particular process/flow. Internal coordination is more flexible, but it's harder to manage, especially for more complex flows.
On the other hand, it's perfect for the Microservice Architecture use case.

The idea is to control the interaction with other services in the coordinator component by using the Workflodize pattern so that each service has an internal workflow that follows the sequence 
and different paths of the interaction. 

For implementing the flow I have choosen to use and experiment with the saga-lib: https://github.com/Domo42/saga-lib

This library would be embedded into the service that needs to perform orchestration, avoiding any central tool or central governance. 


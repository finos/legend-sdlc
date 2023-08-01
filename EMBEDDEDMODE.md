# Embedded Mode
 
## Objective
 
The objective of this document is to provide a definition of Embedded Mode.
 
We are over the age when we need to explain and convince the benefits of modeling the data and contracts.  We have achieved building the models that capture the business intent and using these models to build sophisticated queries and link data.  We also use Legend/Alloy for creating versioned serializable models to build APIs, using schema and model constraints to mandate content quality.  Our focus this time has been primarily concerned with curation of the data, user journeys and development experience.
 
## What do we want to achieve with Embedded mode?
 
**Expand usage of data models** to create a consistent and intuitive experience across all teams at GS.
 
**Enable a developer experience** that is enjoyable with minimal overhead to deal with different code bases.
 
**Enforce high data quality** by using the models natively in the project.
 
**Enforce high testing coverage** by co-locating models within development environment of applications relying on the models.
 
**Enable efficient development process and experimentation** by making model changes available in the same development environment.
 
**Improve operational efficiency** by dealing with one SDLC instead of number of co-ordinations and integrations.
 
## What do we do well?
 
### **Modeling**
 
1. **Studio** allows us to define and document a logical model in Legend language. It does this very well, the language is very easy to describe data entities using the richness of OOP. It also defines relationships, cardinality and optionality well.
2. You are also able to define complex data quality constraints which is possible due to the richness of the legend language. The ability to couple constraints to the data entities is a good thing, but what is even more impressive is the ability to write constraints across associations.
 
### **Code Generation**
 
The code generations can be leveraged to streamline the developer experience.
1. Java Pojos as the internal model in our microservices
2. OpenAPI and Protobuf definitions to autogenerate the input / output parameters for API server side stubs in java for REST or gRPC
 
### **Data Quality - constraints/validations**
 
**The modelling language is very expressive** and has the ability to create complex constraints across data entities.
 
## What would help?
 
Agile experimentation - I would like to make changes to the models, code generations, and have instantaneous feedback on how it will impact the rest of the system.  I would like to have an automated tests that would highlight the impact of the model changes at the development time, and I would like to only deal with one SDLC, I don't want to manage sequential releases, release co-ordinations, partial rollbacks.
 
## Embedded Mode Helps
 
**Modeling**
**Data Quality**
**Code Generation**
**Developer Experience**
 

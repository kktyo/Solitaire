@description('Klondike API: Azure SQL + Container Apps Consumption (no App Service, no ACR, no slots)')
param location string = resourceGroup().location
param prefix string = 'solitaire'
param sqlServerName string = 'kktyo-slt-sql01'
param sqlAdminLogin string
@secure()
param sqlAdminPassword string
@secure()
param jwtAccessSecret string
@secure()
param jwtRefreshSecret string
param containerImage string = 'mcr.microsoft.com/azuredocs/containerapps-helloworld:latest'

var sqlDbName = 'solitaire'
var envName = '${prefix}-cae'
var appName = '${prefix}-api'

resource sql 'Microsoft.Sql/servers@2023-08-01-preview' = {
  name: sqlServerName
  location: location
  properties: {
    administratorLogin: sqlAdminLogin
    administratorLoginPassword: sqlAdminPassword
    minimalTlsVersion: '1.2'
  }
}

resource allowAzure 'Microsoft.Sql/servers/firewallRules@2021-11-01' = {
  parent: sql
  name: 'AllowAllWindowsAzureIps'
  properties: {
    startIpAddress: '0.0.0.0'
    endIpAddress: '0.0.0.0'
  }
}

resource db 'Microsoft.Sql/servers/databases@2023-08-01-preview' = {
  parent: sql
  name: sqlDbName
  location: location
  sku: {
    name: 'Basic'
    tier: 'Basic'
  }
}

resource env 'Microsoft.App/managedEnvironments@2024-03-01' = {
  name: envName
  location: location
  properties: {
    zoneRedundant: false
    appLogsConfiguration: {
      destination: 'none'
    }
  }
}

resource app 'Microsoft.App/containerApps@2024-03-01' = {
  name: appName
  location: location
  properties: {
    managedEnvironmentId: env.id
    configuration: {
      activeRevisionsMode: 'Single'
      ingress: {
        external: true
        targetPort: 8080
        transport: 'http'
        allowInsecure: false
      }
      secrets: [
        { name: 'db-password', value: sqlAdminPassword }
        { name: 'jwt-access', value: jwtAccessSecret }
        { name: 'jwt-refresh', value: jwtRefreshSecret }
      ]
    }
    template: {
      containers: [
        {
          name: 'api'
          image: containerImage
          env: [
            { name: 'PORT', value: '8080' }
            {
              name: 'DB_URL'
              value: 'jdbc:sqlserver://${sql.name}.database.windows.net:1433;databaseName=${sqlDbName};encrypt=true;trustServerCertificate=false;hostNameInCertificate=*.database.windows.net'
            }
            { name: 'DB_USER', value: sqlAdminLogin }
            { name: 'DB_PASSWORD', secretRef: 'db-password' }
            { name: 'JWT_ACCESS_SECRET', secretRef: 'jwt-access' }
            { name: 'JWT_REFRESH_SECRET', secretRef: 'jwt-refresh' }
          ]
          resources: {
            cpu: json('0.5')
            memory: '1Gi'
          }
          probes: [
            {
              type: 'Startup'
              httpGet: {
                path: '/api/v1/health'
                port: 8080
              }
              periodSeconds: 10
              failureThreshold: 30
            }
          ]
        }
      ]
      scale: {
        minReplicas: 0
        maxReplicas: 1
        rules: [
          {
            name: 'http'
            http: {
              metadata: {
                concurrentRequests: '20'
              }
            }
          }
        ]
      }
    }
  }
}

output sqlFqdn string = sql.properties.fullyQualifiedDomainName
output containerAppName string = app.name
output containerAppFqdn string = app.properties.configuration.ingress.fqdn

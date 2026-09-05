@description('Klondike API on App Service Linux containers with staging slot and autoscale')
param location string = resourceGroup().location
param prefix string = 'solitaire'
param sqlAdminLogin string
@secure()
param sqlAdminPassword string
@secure()
param jwtAccessSecret string
@secure()
param jwtRefreshSecret string

var planName = '${prefix}-plan'
var webName = '${prefix}-api'
var sqlServerName = '${prefix}-sql-${uniqueString(resourceGroup().id)}'
var sqlDbName = 'solitaire'
var acrName = replace('${prefix}acr${uniqueString(resourceGroup().id)}', '-', '')

resource acr 'Microsoft.ContainerRegistry/registries@2023-07-01' = {
  name: acrName
  location: location
  sku: {
    name: 'Basic'
  }
  properties: {
    adminUserEnabled: false
  }
}

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

resource plan 'Microsoft.Web/serverfarms@2023-12-01' = {
  name: planName
  location: location
  sku: {
    name: 'S1'
    tier: 'Standard'
    capacity: 1
  }
  kind: 'linux'
  properties: {
    reserved: true
  }
}

resource web 'Microsoft.Web/sites@2023-12-01' = {
  name: webName
  location: location
  identity: {
    type: 'SystemAssigned'
  }
  properties: {
    serverFarmId: plan.id
    httpsOnly: true
    siteConfig: {
      linuxFxVersion: 'DOCKER|mcr.microsoft.com/appsvc/staticsite:latest'
      healthCheckPath: '/api/v1/health'
      acrUseManagedIdentityCreds: true
      appSettings: [
        { name: 'WEBSITES_PORT', value: '8080' }
        { name: 'DB_URL', value: 'jdbc:sqlserver://${sql.name}.database.windows.net:1433;databaseName=${sqlDbName};encrypt=true;trustServerCertificate=false;hostNameInCertificate=*.database.windows.net' }
        { name: 'DB_USER', value: sqlAdminLogin }
        { name: 'DB_PASSWORD', value: sqlAdminPassword }
        { name: 'JWT_ACCESS_SECRET', value: jwtAccessSecret }
        { name: 'JWT_REFRESH_SECRET', value: jwtRefreshSecret }
      ]
    }
  }
}

resource staging 'Microsoft.Web/sites/slots@2023-12-01' = {
  parent: web
  name: 'staging'
  location: location
  identity: {
    type: 'SystemAssigned'
  }
  properties: {
    httpsOnly: true
    siteConfig: {
      healthCheckPath: '/api/v1/health'
      acrUseManagedIdentityCreds: true
      appSettings: [
        { name: 'WEBSITES_PORT', value: '8080' }
        { name: 'DB_URL', value: 'jdbc:sqlserver://${sql.name}.database.windows.net:1433;databaseName=${sqlDbName};encrypt=true;trustServerCertificate=false;hostNameInCertificate=*.database.windows.net' }
        { name: 'DB_USER', value: sqlAdminLogin }
        { name: 'DB_PASSWORD', value: sqlAdminPassword }
        { name: 'JWT_ACCESS_SECRET', value: jwtAccessSecret }
        { name: 'JWT_REFRESH_SECRET', value: jwtRefreshSecret }
      ]
    }
  }
}

resource autoscale 'Microsoft.Insights/autoscalesettings@2022-10-01' = {
  name: '${planName}-autoscale'
  location: location
  properties: {
    enabled: true
    targetResourceUri: plan.id
    profiles: [
      {
        name: 'cpu'
        capacity: {
          minimum: '1'
          maximum: '3'
          default: '1'
        }
        rules: [
          {
            metricTrigger: {
              metricName: 'CpuPercentage'
              metricResourceUri: plan.id
              timeGrain: 'PT1M'
              statistic: 'Average'
              timeWindow: 'PT5M'
              timeAggregation: 'Average'
              operator: 'GreaterThan'
              threshold: 70
            }
            scaleAction: {
              direction: 'Increase'
              type: 'ChangeCount'
              value: '1'
              cooldown: 'PT5M'
            }
          }
          {
            metricTrigger: {
              metricName: 'CpuPercentage'
              metricResourceUri: plan.id
              timeGrain: 'PT1M'
              statistic: 'Average'
              timeWindow: 'PT10M'
              timeAggregation: 'Average'
              operator: 'LessThan'
              threshold: 30
            }
            scaleAction: {
              direction: 'Decrease'
              type: 'ChangeCount'
              value: '1'
              cooldown: 'PT10M'
            }
          }
        ]
      }
    ]
  }
}

output webAppName string = web.name
output acrName string = acr.name
output sqlFqdn string = sql.properties.fullyQualifiedDomainName

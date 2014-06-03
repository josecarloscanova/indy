'use strict';

/* Services */


var aproxServices = angular.module('aprox.services', ['ngResource']);

aproxServices.factory('RemoteSvc', ['$resource',
  function($resource){
    return $resource('/api/1.0/admin/remote/:name', {}, {
      query: {method:'GET', params:{name:''}, isArray:false}
    });
  }]);

aproxServices.factory('HostedSvc', ['$resource',
  function($resource){
    return $resource('/api/1.0/admin/hosted/:name', {}, {
      query: {method:'GET', params:{name:''}, isArray:false}
    });
  }]);

aproxServices.factory('GroupSvc', ['$resource',
  function($resource){
    return $resource('/api/1.0/admin/group/:name', {}, {
      query: {method:'GET', params:{name:''}, isArray:false}
    });
  }]);

aproxServices.factory('FooterSvc', ['$resource',
  function($resource){
    return $resource('/api/1.0/stats/version-info', {}, {
      query: {method:'GET', params:{}, isArray:false}
    });
  }]);


aproxServices.factory('StoreUtilSvc', function(){
    return {
      nameFromKey: function(key){
        return key.substring(key.indexOf(':')+1);
      },

      storeHref: function(key){
        var parts = key.split(':');

        var hostAndPort = window.location.hostname;
        if ( window.location.port != 80 && window.location.port != 443 ){
          hostAndPort += ':';
          hostAndPort += window.location.port;
        }

        // TODO: In-UI browser that allows simple searching
        return window.location.protocol + "//" + hostAndPort + window.location.pathname.replace('/app', '') + 'api/1.0/' + parts[0] + '/' + parts[1] + '/';
      },

      detailHref: function(key){
        var parts = key.split(':');
        return "#/" + parts[0] + "/view/" + parts[1];
      },

      editHref: function(key){
        var parts = key.split(':');
        return "#/" + parts[0] + "/edit/" + parts[1];
      },

      hostedOptions: function(store){
        var options = [];

        if ( store.allow_snapshots ){
          options.push({icon: 'S', title: 'Snapshots allowed'});
        }

        if ( store.allow_releases ){
          options.push({icon: 'R', title: 'Releases allowed'});
        }

        if ( store.allow_snapshots || store.allow_releases ){
          options.push({icon: 'D', title: 'Deployment allowed'});
        }

        return options;
      },

      formatKey: function(type, name){
        return type + ':' + name;
      },

      durationToSeconds: function(duration){
        if ( duration == 'never' ){
          return 0;
        }

        var re=/((\d+)h\s*)?((\d+)m\s*)?((\d+)s?)?/;
        var arry = re.exec( duration );

        var secs = 0;

        if ( arry[2] !== undefined ){
          secs += (parseInt(arry[2]) * 3600 );
        }

        if ( arry[4] !== undefined ){
          secs += (parseInt(arry[4]) * 60 );
        }

        if ( arry[6] !== undefined ){
          secs += (parseInt(arry[6]));
        }

        return secs;
      },

      secondsToDuration: function(secs){
        if ( secs == undefined ){
          return 'never';
        }

        if ( secs < 1 ){
          return 'never';
        }

        var hours = Math.floor(secs / (60 * 60));

        var mdiv = secs % (60 * 60);
        var minutes = Math.floor(mdiv / 60);

        var sdiv = mdiv % 60;
        var seconds = Math.ceil(sdiv);

        var out = '';
        if ( hours > 0 ){
          out += hours + 'h';
        }

        if ( minutes > 0 ){
          if ( out.length > 0 ){ out += ' '; }

          out += minutes + 'm';
        }

        if ( seconds > 0 ){
          if ( out.length > 0 ){ out += ' '; }

          out += seconds + 's';
        }

        return out;
      },

      showCacheTimeout: function(store){
        return !store.is_passthrough;
      },

    };
  });

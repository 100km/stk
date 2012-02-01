function(doc) {
  if (doc.bib && doc.times && doc.times.length > 0 && doc.site_id != undefined) {
    var len = doc.times.length;
      emit([doc.race_id, -len, -doc.site_id, doc.times[len-1]], doc);
  }
};